import { Canvas, useFrame } from '@react-three/fiber';
import { Html, Line, OrbitControls } from '@react-three/drei';
import { useMemo, useRef, useState } from 'react';
import * as THREE from 'three';
import { pixelRatioCap } from './webgl.js';
import { HOME, SEV_HEX, coordsFor, severityForType } from './geo.js';
import countriesTopo from 'world-atlas/countries-110m.json';
import { mesh as topoMesh } from 'topojson-client';

const R = 1.4;
const MAX_ARCS = 40;

/** Project [lat, lon] onto a sphere of the given radius (kept here so geo.js stays three-free). */
function latLonToVec3(lat, lon, radius) {
  const phi = (90 - lat) * (Math.PI / 180);
  const theta = (lon + 180) * (Math.PI / 180);
  return new THREE.Vector3(
    -radius * Math.sin(phi) * Math.cos(theta),
    radius * Math.cos(phi),
    radius * Math.sin(phi) * Math.sin(theta),
  );
}

/** Every country border, drawn as line segments projected onto the sphere (all countries shown). */
function CountryBorders() {
  const positions = useMemo(() => {
    const borders = topoMesh(countriesTopo, countriesTopo.objects.countries); // MultiLineString
    const pts = [];
    for (const line of borders.coordinates) {
      for (let i = 0; i < line.length - 1; i++) {
        const a = latLonToVec3(line[i][1], line[i][0], R * 1.002);
        const b = latLonToVec3(line[i + 1][1], line[i + 1][0], R * 1.002);
        pts.push(a.x, a.y, a.z, b.x, b.y, b.z);
      }
    }
    return new Float32Array(pts);
  }, []);
  return (
    <lineSegments>
      <bufferGeometry>
        <bufferAttribute attach="attributes-position" array={positions} count={positions.length / 3} itemSize={3} />
      </bufferGeometry>
      <lineBasicMaterial color="#6fc3ff" transparent opacity={0.55} />
    </lineSegments>
  );
}

function Arc({ from, to, color, speed }) {
  const dot = useRef();
  const curve = useMemo(() => {
    const mid = from.clone().add(to).multiplyScalar(0.5);
    mid.normalize().multiplyScalar(R + from.distanceTo(to) * 0.4);
    return new THREE.QuadraticBezierCurve3(from, mid, to);
  }, [from, to]);
  const points = useMemo(() => curve.getPoints(40), [curve]);
  useFrame((state) => {
    const t = (state.clock.elapsedTime * speed) % 1;
    if (dot.current) dot.current.position.copy(curve.getPoint(t));
  });
  return (
    <group>
      <Line points={points} color={color} transparent opacity={0.45} lineWidth={1} />
      <mesh ref={dot}>
        <sphereGeometry args={[0.018, 8, 8]} />
        <meshBasicMaterial color={color} />
      </mesh>
    </group>
  );
}

function OriginPoint({ pos, flow }) {
  const [hover, setHover] = useState(false);
  const color = SEV_HEX[severityForType(flow.topType)] || SEV_HEX.LOW;
  return (
    <mesh position={pos} onPointerOver={() => setHover(true)} onPointerOut={() => setHover(false)}>
      <sphereGeometry args={[0.03, 10, 10]} />
      <meshBasicMaterial color={color} />
      {hover && (
        <Html distanceFactor={8} style={{ pointerEvents: 'none' }}>
          <div style={{ background: 'var(--bg-elev)', border: '1px solid var(--border-strong)',
            borderRadius: 6, padding: '4px 8px', fontSize: 12, whiteSpace: 'nowrap', color: 'var(--text)' }}>
            <strong>{flow.country}</strong> · {flow.count} events<br />top: {flow.topType}
          </div>
        </Html>
      )}
    </mesh>
  );
}

function Globe({ flows }) {
  const group = useRef();
  const controls = useRef();
  const home = useMemo(() => latLonToVec3(HOME[0], HOME[1], R), []);
  const arcs = useMemo(() => flows
    .map((f) => ({ f, c: coordsFor(f.country) }))
    .filter((x) => x.c)
    .slice(0, MAX_ARCS)
    .map(({ f, c }) => {
      const pos = latLonToVec3(c[0], c[1], R);
      const sev = severityForType(f.topType);
      return { f, pos, color: SEV_HEX[sev] || SEV_HEX.LOW, speed: 0.2 + Math.min(0.6, f.count / 50) };
    }), [flows]);

  // Auto-rotate; pause while the user is interacting.
  const [paused, setPaused] = useState(false);
  useFrame(() => { if (group.current && !paused) group.current.rotation.y += 0.0015; });

  return (
    <group ref={group}>
      {/* Solid ocean sphere (lit so it reads as a globe, not a black ball). */}
      <mesh>
        <sphereGeometry args={[R * 0.99, 48, 48]} />
        <meshStandardMaterial color="#13344f" emissive="#0b2238" emissiveIntensity={0.6}
          metalness={0.2} roughness={0.75} />
      </mesh>
      {/* Faint lat/long graticule under the country borders. */}
      <mesh>
        <sphereGeometry args={[R, 36, 24]} />
        <meshBasicMaterial color="#2f6b9e" wireframe transparent opacity={0.12} />
      </mesh>
      {/* Real country borders. */}
      <CountryBorders />
      {/* Subtle atmosphere glow. */}
      <mesh>
        <sphereGeometry args={[R * 1.08, 48, 48]} />
        <meshBasicMaterial color="#3b8eea" transparent opacity={0.08} side={THREE.BackSide} />
      </mesh>
      <mesh position={home}>
        <sphereGeometry args={[0.05, 14, 14]} />
        <meshBasicMaterial color="#3ddc97" />
      </mesh>
      {arcs.map((a, i) => (
        <group key={a.f.country + i}>
          <OriginPoint pos={a.pos} flow={a.f} />
          <Arc from={a.pos} to={home} color={a.color} speed={a.speed} />
        </group>
      ))}
      <OrbitControls ref={controls} enablePan={false} enableZoom={false}
        onStart={() => setPaused(true)} onEnd={() => setPaused(false)} />
    </group>
  );
}

/** 3D attack-origin globe. `active` pauses the render loop when off-screen/hidden. */
export default function AttackGlobe({ flows = [], active = true, quality = 'high' }) {
  return (
    <Canvas
      style={{ width: '100%', height: '100%' }}
      dpr={pixelRatioCap(quality)}
      frameloop={active ? 'always' : 'never'}
      gl={{ antialias: quality !== 'low', powerPreference: 'default' }}
      camera={{ position: [0, 0, 4], fov: 45 }}
    >
      <ambientLight intensity={0.6} />
      <pointLight position={[5, 3, 5]} intensity={1.1} />
      <Globe flows={flows} />
    </Canvas>
  );
}
