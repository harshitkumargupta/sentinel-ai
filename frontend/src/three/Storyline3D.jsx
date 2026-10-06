import { Canvas, useFrame } from '@react-three/fiber';
import { Html, Line, OrbitControls } from '@react-three/drei';
import { useMemo, useRef, useState } from 'react';
import * as THREE from 'three';
import { pixelRatioCap } from './webgl.js';

const TYPE_COLOR = {
  incident: '#f85149', alert: '#f0883e', user: '#2f81f7', ip: '#3ddc97',
  resource: '#a371f7', honeytoken: '#d29922', aggregate: '#8b949e',
};
const COLUMN = { incident: 0, alert: 1, user: 2, ip: 3, resource: 4, honeytoken: 4, aggregate: 4 };

function layout(nodes) {
  const perCol = {};
  const map = {};
  nodes.forEach((n) => {
    const col = COLUMN[n.type] ?? 4;
    perCol[col] = (perCol[col] || 0) + 1;
    const row = perCol[col];
    map[n.id] = new THREE.Vector3((col - 2) * 1.6, 0, 0); // x by column; y/z set below
    map[n.id]._col = col; map[n.id]._row = row;
  });
  // Spread rows vertically, centered per column.
  Object.values(map).forEach((v) => {
    const count = perCol[v._col];
    v.y = (v._row - (count + 1) / 2) * 0.9;
    v.z = ((v._col % 2) - 0.5) * 0.6;
  });
  return map;
}

function Node({ pos, node }) {
  const [hover, setHover] = useState(false);
  const color = TYPE_COLOR[node.type] || '#8b949e';
  return (
    <mesh position={pos} onPointerOver={() => setHover(true)} onPointerOut={() => setHover(false)}>
      <sphereGeometry args={[0.16, 14, 14]} />
      <meshStandardMaterial color={color} emissive={color} emissiveIntensity={0.4} />
      {hover && (
        <Html distanceFactor={9} style={{ pointerEvents: 'none' }}>
          <div style={{ background: 'var(--bg-elev)', border: '1px solid var(--border-strong)', borderRadius: 6,
            padding: '3px 7px', fontSize: 11, whiteSpace: 'nowrap', color: 'var(--text)' }}>
            {node.type}: {node.label || node.id}
          </div>
        </Html>
      )}
    </mesh>
  );
}

function Scene({ graph }) {
  const group = useRef();
  const map = useMemo(() => layout(graph.nodes || []), [graph]);
  useFrame(() => { if (group.current) group.current.rotation.y += 0.001; });
  return (
    <group ref={group}>
      {(graph.edges || []).map((e, i) => {
        const a = map[e.source]; const b = map[e.target];
        if (!a || !b) return null;
        return <Line key={i} points={[a, b]} color="#57606a" lineWidth={1} transparent opacity={0.5} />;
      })}
      {(graph.nodes || []).map((n) => map[n.id] && <Node key={n.id} pos={map[n.id]} node={n} />)}
      <OrbitControls enablePan={false} />
    </group>
  );
}

/** 3D attack storyline: typed nodes in depth columns with typed edges. Same data as the 2D graph. */
export default function Storyline3D({ graph, active = true, quality = 'high' }) {
  return (
    <Canvas style={{ width: '100%', height: '100%' }} dpr={pixelRatioCap(quality)}
      frameloop={active ? 'always' : 'never'} gl={{ antialias: quality !== 'low' }}
      camera={{ position: [0, 0, 7], fov: 50 }}>
      <ambientLight intensity={0.7} />
      <pointLight position={[5, 5, 5]} intensity={1} />
      <Scene graph={graph} />
    </Canvas>
  );
}
