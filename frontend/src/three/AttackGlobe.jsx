import { Canvas, useFrame, useThree } from '@react-three/fiber';
import { Html, Line, OrbitControls, Stars, useGLTF } from '@react-three/drei';
import { useEffect, useMemo, useRef, useState } from 'react';
import * as THREE from 'three';
import { pixelRatioCap } from './webgl.js';
import { SEV_HEX, coordsFor, latLonToXYZ } from './geo.js';
import { GLOBE_CONFIG } from './globeConfig.js';
import { aggregateFlows, arcOpacity, isExpired, pulseSpeed, thicknessForCount } from './flows.js';

const MODEL_URL = '/models/earth.glb';
const R = 1.4; // on-screen globe radius the overlay is calibrated to.

// Load the model outside the main chunk and warm the cache so it is ready by the time the lazy
// 3D wrapper mounts. Preloading here (not in the main bundle) keeps the model off the critical path.
useGLTF.preload(MODEL_URL);

/** THREE.Vector3 for a geographic point, using the shared pure projection + longitude calibration. */
function vecFor(lat, lon, radius) {
  const { x, y, z } = latLonToXYZ(lat, lon, radius, GLOBE_CONFIG.yawDeg);
  return new THREE.Vector3(x, y, z);
}

/**
 * The imported earth exactly as authored: three concentric meshes (earth / clouds / atmosphere)
 * with their original textures. We only center it and uniformly scale the EARTH mesh to radius R —
 * no re-texturing, recoloring or geometry edits. Materials are left untouched.
 */
function EarthModel() {
  const { scene } = useGLTF(MODEL_URL);
  const prepared = useMemo(() => {
    const root = scene.clone(true);
    // Scale so the earth surface sits at radius R (clouds/atmosphere shells ride just above it).
    let earthR = 0;
    root.traverse((o) => {
      if (o.isMesh && /earth/i.test(o.material?.name || o.name || '')) {
        o.geometry.computeBoundingSphere();
        earthR = o.geometry.boundingSphere.radius;
      }
    });
    if (!earthR) {
      const sphere = new THREE.Box3().setFromObject(root).getBoundingSphere(new THREE.Sphere());
      earthR = sphere.radius * 0.988; // fall back from the atmosphere shell to the earth surface.
    }
    root.scale.setScalar(R / earthR);
    const center = new THREE.Box3().setFromObject(root).getCenter(new THREE.Vector3());
    root.position.sub(center);
    return root;
  }, [scene]);
  return <primitive object={prepared} />;
}

/** One great-circle arc with a travelling pulse and an impact ripple at the target. */
function Arc({ country, from, to, color, thickness, speed, lastSeenRef, fadeSeconds, segments }) {
  const lineRef = useRef();
  const dotRef = useRef();
  const rippleRef = useRef();

  const curve = useMemo(() => {
    const mid = from.clone().add(to).multiplyScalar(0.5);
    mid.normalize().multiplyScalar(R + from.distanceTo(to) * 0.4); // lift above the surface.
    return new THREE.QuadraticBezierCurve3(from, mid, to);
  }, [from, to]);
  const points = useMemo(() => curve.getPoints(segments), [curve, segments]);

  // Orient the impact ring tangent to the sphere at the target.
  const rippleQuat = useMemo(() => {
    const q = new THREE.Quaternion();
    q.setFromUnitVectors(new THREE.Vector3(0, 0, 1), to.clone().normalize());
    return q;
  }, [to]);

  useFrame((state) => {
    const seen = lastSeenRef.current.get(country) ?? state.clock.elapsedTime * 1000;
    const age = (performance.now() - seen) / 1000;
    const op = arcOpacity(age, fadeSeconds);
    if (lineRef.current?.material) lineRef.current.material.opacity = 0.25 + 0.6 * op;
    const t = (state.clock.elapsedTime * speed) % 1;
    if (dotRef.current) {
      dotRef.current.position.copy(curve.getPoint(t));
      dotRef.current.material.opacity = op;
    }
    if (rippleRef.current) {
      // Ripple grows as the pulse nears the target, then resets — one impact per pulse cycle.
      const phase = Math.max(0, (t - 0.8) / 0.2);
      rippleRef.current.scale.setScalar(0.3 + phase * 1.6);
      rippleRef.current.material.opacity = phase * op;
    }
  });

  return (
    <group>
      <Line ref={lineRef} points={points} color={color} transparent opacity={0.6} lineWidth={thickness} />
      <mesh ref={dotRef}>
        <sphereGeometry args={[0.02, 10, 10]} />
        <meshBasicMaterial color={color} transparent />
      </mesh>
      <mesh ref={rippleRef} position={to} quaternion={rippleQuat}>
        <ringGeometry args={[0.03, 0.05, 24]} />
        <meshBasicMaterial color={color} transparent opacity={0} side={THREE.DoubleSide} />
      </mesh>
    </group>
  );
}

/** A glowing source marker, sized by attack count, with a hover tooltip and click-to-filter. */
function OriginMarker({ flow, pos, onSelect }) {
  const color = SEV_HEX[flow.severity] || SEV_HEX.LOW;
  const size = 0.022 + 0.03 * Math.min(1, Math.log2(1 + flow.count) / Math.log2(51));
  const [hover, setHover] = useState(false);
  const halo = useRef();
  useFrame((state) => {
    if (halo.current) halo.current.scale.setScalar(1 + 0.15 * Math.sin(state.clock.elapsedTime * 3));
  });
  return (
    <group position={pos}>
      <mesh
        onPointerOver={(e) => { e.stopPropagation(); setHover(true); document.body.style.cursor = 'pointer'; }}
        onPointerOut={() => { setHover(false); document.body.style.cursor = 'default'; }}
        onClick={(e) => { e.stopPropagation(); onSelect?.(flow.country); }}
      >
        <sphereGeometry args={[size, 12, 12]} />
        <meshBasicMaterial color={color} />
      </mesh>
      <mesh ref={halo}>
        <sphereGeometry args={[size * 1.8, 12, 12]} />
        <meshBasicMaterial color={color} transparent opacity={0.25} />
      </mesh>
      {hover && (
        <Html distanceFactor={9} style={{ pointerEvents: 'none' }} zIndexRange={[20, 0]}>
          <div className="globe-tip">
            <strong>{flow.country}</strong> · {flow.count} attacks<br />
            top: {flow.topType || '—'} · <span style={{ color }}>{flow.severity}</span>
          </div>
        </Html>
      )}
    </group>
  );
}

function Scene({ flows, quality, onSelect }) {
  const spin = useRef();
  const controls = useRef();
  const paused = useRef(false);
  const lastSeenRef = useRef(new Map());

  const site = GLOBE_CONFIG.site;
  const sitePos = useMemo(() => vecFor(site.lat, site.lon, R * 1.01), [site.lat, site.lon]);
  const segments = quality === 'low' ? GLOBE_CONFIG.arcSegments.low : GLOBE_CONFIG.arcSegments.high;

  // Aggregate repeated origins into one arc each, capped; stamp "last seen" for the fade clock.
  const arcs = useMemo(() => {
    const agg = aggregateFlows(flows, GLOBE_CONFIG.maxArcs);
    const now = performance.now();
    const map = lastSeenRef.current;
    agg.forEach((f) => map.set(f.country, now));
    // Drop lines whose origin went quiet long enough to fully fade.
    for (const [c, seen] of map) {
      if (isExpired((now - seen) / 1000, GLOBE_CONFIG.fadeSeconds) && !agg.find((f) => f.country === c)) {
        map.delete(c);
      }
    }
    return agg
      .map((f) => ({ f, c: coordsFor(f.country) }))
      .filter((x) => x.c)
      .map(({ f, c }) => ({
        flow: f,
        pos: vecFor(c[0], c[1], R * 1.01),
        color: SEV_HEX[f.severity] || SEV_HEX.LOW,
        thickness: thicknessForCount(f.count),
        speed: pulseSpeed(f.count),
      }));
  }, [flows]);

  useFrame(() => { if (spin.current && !paused.current) spin.current.rotation.y += 0.0012; });

  return (
    <>
      <Stars radius={60} depth={30} count={1500} factor={3} saturation={0} fade speed={0.5} />
      {/* Soft, even backdrop lighting so the model's own textures read clearly (day side + gentle relief);
          this lights the scene without recolouring the model. */}
      <ambientLight intensity={1.25} />
      <hemisphereLight intensity={0.5} color="#bcd6ff" groundColor="#20304a" />
      <directionalLight position={[5, 3, 5]} intensity={0.8} />
      <group ref={spin}>
        <EarthModel />
        {/* Protected site marker. */}
        <mesh position={sitePos}>
          <sphereGeometry args={[0.035, 14, 14]} />
          <meshBasicMaterial color="#8be9fd" />
        </mesh>
        {arcs.map((a) => (
          <group key={a.flow.country}>
            <OriginMarker flow={a.flow} pos={a.pos} onSelect={onSelect} />
            <Arc
              country={a.flow.country} from={a.pos} to={sitePos} color={a.color}
              thickness={a.thickness} speed={a.speed} segments={segments}
              lastSeenRef={lastSeenRef} fadeSeconds={GLOBE_CONFIG.fadeSeconds}
            />
          </group>
        ))}
      </group>
      <OrbitControls
        ref={controls} enablePan={false} enableZoom={false} rotateSpeed={0.5}
        onStart={() => { paused.current = true; }} onEnd={() => { paused.current = false; }}
      />
    </>
  );
}

/** Frees GPU resources (geometries, materials, textures, the cached GLTF) when the globe unmounts. */
function DisposeOnUnmount() {
  const { gl, scene } = useThree();
  useEffect(() => () => {
    scene.traverse((o) => {
      if (o.geometry) o.geometry.dispose();
      const mats = Array.isArray(o.material) ? o.material : o.material ? [o.material] : [];
      mats.forEach((m) => { Object.values(m).forEach((v) => v?.isTexture && v.dispose()); m.dispose(); });
    });
    useGLTF.clear(MODEL_URL);
    gl.dispose();
  }, [gl, scene]);
  return null;
}

/** 3D attack globe on the real earth model. `active` pauses the render loop when off-screen/hidden. */
export default function AttackGlobe({ flows = [], active = true, quality = 'high', onSelect }) {
  // Frame the protected site on first paint so the destination (and the arcs converging on it) are
  // front-and-centre; the globe then auto-rotates from there.
  const camPos = useMemo(() => {
    const s = GLOBE_CONFIG.site;
    const { x, y, z } = latLonToXYZ(s.lat, s.lon, 1, GLOBE_CONFIG.yawDeg);
    const d = 4;
    return [x * d, y * d, z * d];
  }, []);
  return (
    <Canvas
      style={{ width: '100%', height: '100%' }}
      dpr={pixelRatioCap(quality)}
      frameloop={active ? 'always' : 'never'}
      gl={{ antialias: quality !== 'low', powerPreference: 'default' }}
      camera={{ position: camPos, fov: 45 }}
    >
      <DisposeOnUnmount />
      <Scene flows={flows} quality={quality} onSelect={onSelect} />
    </Canvas>
  );
}
