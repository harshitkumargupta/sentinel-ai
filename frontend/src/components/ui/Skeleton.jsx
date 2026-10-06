/** Skeleton loader block. Respects reduced motion via the shimmer animation guard in tokens.css. */
export default function Skeleton({ width = '100%', height = 16, radius, style, ...props }) {
  return (
    <span
      className="ui-skel"
      aria-hidden="true"
      style={{ display: 'block', width, height, borderRadius: radius, ...style }}
      {...props}
    />
  );
}

/** A few stacked skeleton lines, for loading states. */
export function SkeletonLines({ lines = 3 }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }} aria-busy="true" aria-live="polite">
      {Array.from({ length: lines }).map((_, i) => (
        <Skeleton key={i} width={`${90 - i * 12}%`} />
      ))}
    </div>
  );
}
