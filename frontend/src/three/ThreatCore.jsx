import { useFrame } from '@react-three/fiber';
import { Canvas } from '@react-three/fiber';
import { useMemo, useRef } from 'react';
import * as THREE from 'three';
import { pixelRatioCap } from './webgl.js';

const LEVEL_COLOR = {
  LOW: '#2f81f7', MEDIUM: '#d29922', HIGH: '#f0883e', CRITICAL: '#f85149',
};
const LEVEL_SPEED = { LOW: 0.6, MEDIUM: 1.1, HIGH: 1.8, CRITICAL: 2.6 };

function Orb({ level }) {
  const mesh = useRef();
  const color = useMemo(() => new THREE.Color(LEVEL_COLOR[level] || LEVEL_COLOR.LOW), [level]);
  const speed = LEVEL_SPEED[level] || 1;
  useFrame((state) => {
    const t = state.clock.elapsedTime;
    const s = 1 + Math.sin(t * speed * Math.PI) * 0.06;
    if (mesh.current) {
      mesh.current.scale.setScalar(s);
      mesh.current.rotation.y = t * 0.3;
    }
  });
  return (
    <mesh ref={mesh}>
      <icosahedronGeometry args={[1, 1]} />
      <meshStandardMaterial color={color} emissive={color} emissiveIntensity={0.6}
        metalness={0.3} roughness={0.3} flatShading />
    </mesh>
  );
}

/** Small animated risk orb. `level` drives color + pulse speed. `active` pauses the render loop. */
export default function ThreatCore({ size = 28, level = 'LOW', active = true, quality = 'high' }) {
  return (
    <Canvas
      style={{ width: size, height: size }}
      dpr={pixelRatioCap(quality)}
      frameloop={active ? 'always' : 'never'}
      gl={{ antialias: quality !== 'low', powerPreference: 'low-power' }}
      camera={{ position: [0, 0, 3], fov: 45 }}
    >
      <ambientLight intensity={0.5} />
      <pointLight position={[3, 3, 3]} intensity={1.2} />
      <Orb level={level} />
    </Canvas>
  );
}
