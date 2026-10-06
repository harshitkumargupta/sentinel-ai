import { Canvas, useFrame } from '@react-three/fiber';
import { useMemo, useRef } from 'react';
import * as THREE from 'three';
import { pixelRatioCap } from './webgl.js';

function Particles({ count }) {
  const ref = useRef();
  const positions = useMemo(() => {
    const arr = new Float32Array(count * 3);
    for (let i = 0; i < count; i++) {
      arr[i * 3] = (Math.random() - 0.5) * 12;
      arr[i * 3 + 1] = (Math.random() - 0.5) * 8;
      arr[i * 3 + 2] = (Math.random() - 0.5) * 6;
    }
    return arr;
  }, [count]);
  useFrame((state) => { if (ref.current) ref.current.rotation.y = state.clock.elapsedTime * 0.03; });
  return (
    <points ref={ref}>
      <bufferGeometry>
        <bufferAttribute attach="attributes-position" count={count} array={positions} itemSize={3} />
      </bufferGeometry>
      <pointsMaterial size={0.035} color="#3b8eea" transparent opacity={0.7} sizeAttenuation />
    </points>
  );
}

/** Subtle particle field behind the login card. Particle count scales down in Low quality. */
export default function LoginBackdrop({ quality = 'high' }) {
  const count = quality === 'low' ? 200 : 700;
  return (
    <Canvas style={{ position: 'absolute', inset: 0 }} dpr={pixelRatioCap(quality)}
      gl={{ antialias: false, powerPreference: 'low-power' }} camera={{ position: [0, 0, 6], fov: 60 }}>
      <Particles count={count} />
    </Canvas>
  );
}
