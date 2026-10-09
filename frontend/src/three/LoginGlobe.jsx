import { Canvas, useFrame } from '@react-three/fiber';
import { useMemo, useRef } from 'react';
import * as THREE from 'three';
import { pixelRatioCap } from './webgl.js';

function llToV3(lat, lon, r = 2.02) {
  const phi = (90 - lat) * (Math.PI / 180);
  const theta = (lon + 180) * (Math.PI / 180);
  return new THREE.Vector3(
    -r * Math.sin(phi) * Math.cos(theta),
    r * Math.cos(phi),
    r * Math.sin(phi) * Math.sin(theta),
  );
}

const CITIES = [
  [51.5, -0.1],    // London
  [40.7, -74.0],   // NYC
  [37.8, -122.4],  // SF
  [48.9, 2.3],     // Paris
  [35.7, 139.7],   // Tokyo
  [1.3, 103.8],    // Singapore
  [25.2, 55.3],    // Dubai
  [19.1, 72.9],    // Mumbai
  [-23.5, -46.6],  // Sao Paulo
  [52.5, 13.4],    // Berlin
  [43.7, -79.4],   // Toronto
  [31.2, 121.5],   // Shanghai
];

const THREATS = [
  [55.8, 37.6],   // Moscow
  [39.9, 116.4],  // Beijing
  [36.0, 128.0],  // Korea region
];

const CITY_CONNECTIONS = [
  [0, 1], [1, 2], [0, 3], [1, 9], [3, 9],
  [4, 5], [5, 7], [4, 11], [6, 7],
  [10, 1], [11, 4], [2, 10], [3, 5], [0, 8],
];

function makeArc(a, b, segments = 28) {
  const mid = a.clone().add(b).multiplyScalar(0.5);
  const dist = a.distanceTo(b);
  const lift = 1 + (dist / 14) * 0.22;
  mid.normalize().multiplyScalar(2.02 * lift);
  return new THREE.QuadraticBezierCurve3(a, mid, b).getPoints(segments);
}

function Stars({ count }) {
  const positions = useMemo(() => {
    const arr = new Float32Array(count * 3);
    for (let i = 0; i < count; i++) {
      arr[i * 3] = (Math.random() - 0.5) * 24;
      arr[i * 3 + 1] = (Math.random() - 0.5) * 18;
      arr[i * 3 + 2] = (Math.random() - 0.5) * 8 - 3;
    }
    return arr;
  }, [count]);

  return (
    <points>
      <bufferGeometry>
        <bufferAttribute attach="attributes-position" count={count} array={positions} itemSize={3} />
      </bufferGeometry>
      <pointsMaterial size={0.016} color="#4db5f5" transparent opacity={0.32} sizeAttenuation />
    </points>
  );
}

function GlobeScene() {
  const groupRef = useRef();
  const threatMeshRefs = useRef([]);

  const cityPos = useMemo(() => CITIES.map(([lat, lon]) => llToV3(lat, lon)), []);
  const threatPos = useMemo(() => THREATS.map(([lat, lon]) => llToV3(lat, lon)), []);

  const arcLines = useMemo(() => {
    return CITY_CONNECTIONS.map(([ci, cj]) => {
      const pts = makeArc(cityPos[ci], cityPos[cj]);
      const geo = new THREE.BufferGeometry().setFromPoints(pts);
      const mat = new THREE.LineBasicMaterial({ color: 0x22d3ee, transparent: true, opacity: 0.38 });
      return new THREE.Line(geo, mat);
    });
  }, [cityPos]);

  const threatLines = useMemo(() => {
    return THREATS.flatMap((_, ti) =>
      [0, 1, 2, 3].map((ci) => {
        const pts = makeArc(threatPos[ti], cityPos[ci], 18);
        const geo = new THREE.BufferGeometry().setFromPoints(pts);
        const mat = new THREE.LineBasicMaterial({ color: 0xff4444, transparent: true, opacity: 0.1 });
        return new THREE.Line(geo, mat);
      }),
    );
  }, [cityPos, threatPos]);

  useFrame(({ clock }) => {
    if (groupRef.current) groupRef.current.rotation.y = clock.elapsedTime * 0.05;
    const t = clock.elapsedTime;
    threatMeshRefs.current.forEach((m, i) => {
      if (!m) return;
      const pulse = 0.5 + 0.5 * Math.sin(t * 1.8 + i * 1.3);
      m.material.opacity = 0.55 + 0.45 * pulse;
      m.scale.setScalar(1 + 0.35 * pulse);
    });
  });

  return (
    <group ref={groupRef}>
      {/* Core dark sphere */}
      <mesh>
        <sphereGeometry args={[2, 48, 32]} />
        <meshStandardMaterial color="#051226" emissive="#0a1f3a" emissiveIntensity={0.6} roughness={0.6} metalness={0.2} />
      </mesh>

      {/* Wireframe grid overlay */}
      <mesh>
        <sphereGeometry args={[2.008, 36, 22]} />
        <meshBasicMaterial color="#22608a" wireframe transparent opacity={0.22} />
      </mesh>

      {/* Rim glow — front face so the sphere edge lights up */}
      <mesh>
        <sphereGeometry args={[2.02, 32, 32]} />
        <meshBasicMaterial color="#00c8e8" transparent opacity={0.06} side={THREE.FrontSide} />
      </mesh>

      {/* Inner atmosphere glow */}
      <mesh>
        <sphereGeometry args={[2.18, 32, 32]} />
        <meshBasicMaterial color="#00c8e8" transparent opacity={0.09} side={THREE.BackSide} />
      </mesh>

      {/* Outer atmosphere halo */}
      <mesh>
        <sphereGeometry args={[2.55, 32, 32]} />
        <meshBasicMaterial color="#0060b8" transparent opacity={0.04} side={THREE.BackSide} />
      </mesh>

      {/* City connection arcs */}
      {arcLines.map((l, i) => <primitive key={`arc-${i}`} object={l} />)}

      {/* Threat arc lines */}
      {threatLines.map((l, i) => <primitive key={`tl-${i}`} object={l} />)}

      {/* City nodes */}
      {cityPos.map((pos, i) => (
        <mesh key={`city-${i}`} position={pos}>
          <sphereGeometry args={[0.03, 8, 8]} />
          <meshBasicMaterial color="#38bdf8" transparent opacity={0.9} />
        </mesh>
      ))}

      {/* Threat nodes (pulsing red) */}
      {threatPos.map((pos, i) => (
        <mesh key={`threat-${i}`} position={pos} ref={(el) => { threatMeshRefs.current[i] = el; }}>
          <sphereGeometry args={[0.035, 8, 8]} />
          <meshBasicMaterial color="#ff3333" transparent opacity={0.8} />
        </mesh>
      ))}
    </group>
  );
}

/** Decorative login globe — no live data, purely visual. */
export default function LoginGlobe({ quality = 'high' }) {
  const starCount = quality === 'low' ? 200 : 500;
  return (
    <Canvas
      style={{ position: 'absolute', inset: 0 }}
      dpr={pixelRatioCap(quality)}
      gl={{ antialias: quality !== 'low', powerPreference: 'default', alpha: true }}
      camera={{ position: [0, 0.6, 5.6], fov: 50 }}
    >
      <ambientLight intensity={0.3} color="#0a3060" />
      <pointLight position={[-4, 2, 4]} intensity={4} color="#00c8e8" />
      <pointLight position={[4, -2, -4]} intensity={1.5} color="#ff3333" />
      <pointLight position={[0, 5, 2]} intensity={1.2} color="#0060b8" />
      <Stars count={starCount} />
      <GlobeScene />
    </Canvas>
  );
}
