import { Canvas } from '@react-three/fiber';
import { Html, Line, OrbitControls } from '@react-three/drei';
import { pixelRatioCap } from './webgl.js';
import { SEVERITY_COLOR } from './attackGraphModel.js';

const SHAPE = { ip: 'sphere', user: 'box', host: 'octa' };

function NodeMesh({ node, pos, selected, onSelect }) {
  const color = SEVERITY_COLOR[node.severity] || '#8b949e';
  const scale = 0.18 + Math.min(0.14, Math.log10(1 + node.count) * 0.08);
  return (
    <group position={pos}>
      <mesh scale={selected ? scale * 1.4 : scale} onClick={(e) => { e.stopPropagation(); onSelect(node); }}>
        {SHAPE[node.type] === 'box' ? <boxGeometry args={[1.6, 1.6, 1.6]} />
          : SHAPE[node.type] === 'octa' ? <octahedronGeometry args={[1.2]} /> : <sphereGeometry args={[1, 18, 18]} />}
        <meshStandardMaterial color={color} emissive={color} emissiveIntensity={selected ? 0.8 : 0.35} />
      </mesh>
      <Html distanceFactor={10} position={[0, -0.42, 0]} center style={{ pointerEvents: 'none', fontSize: 11, whiteSpace: 'nowrap', color: '#c9d1d9' }}>
        {node.label}
      </Html>
    </group>
  );
}

/** Interactive 3D attack graph (IP spheres → user cubes → host octahedra), severity-coloured. */
export default function AttackGraph3D({ nodes, edges, positions, selectedId, onSelect, quality = 'high', active = true }) {
  const p = (id) => { const [x, y] = positions[id] || [0, 0]; return [x * 2.4, y * 1.1, 0]; };
  return (
    <Canvas frameloop={active ? 'always' : 'never'} dpr={[1, pixelRatioCap(quality)]} camera={{ position: [0, 0, 7], fov: 50 }}
      onPointerMissed={() => onSelect(null)}>
      <ambientLight intensity={0.6} />
      <pointLight position={[4, 4, 6]} intensity={1.2} />
      {edges.map((e) => (
        <Line key={e.id} points={[p(e.source), p(e.target)]} color={SEVERITY_COLOR[e.severity] || '#8b949e'}
          lineWidth={1 + Math.min(4, Math.log2(1 + e.count))} transparent opacity={0.8} />
      ))}
      {nodes.map((n) => <NodeMesh key={n.id} node={n} pos={p(n.id)} selected={n.id === selectedId} onSelect={onSelect} />)}
      <OrbitControls enablePan={false} minDistance={3} maxDistance={14} />
    </Canvas>
  );
}
