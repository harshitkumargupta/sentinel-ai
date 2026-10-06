import { useMemo, useState } from 'react';

/**
 * Table — sortable columns, sticky header, and light row virtualization for large lists.
 * Props:
 *   columns: [{ key, header, render?(row), sortable?, sortValue?(row), width? }]
 *   rows: any[]; rowKey(row): string|number; newRowKeys?: Set (flash highlight for live inserts)
 *   maxHeight (default 420), rowHeight (default 40), virtualizeThreshold (default 80)
 */
export default function Table({
  columns, rows, rowKey, newRowKeys,
  maxHeight = 420, rowHeight = 40, virtualizeThreshold = 80, emptyLabel = 'No rows',
}) {
  const [sort, setSort] = useState({ key: null, dir: 'asc' });
  const [scrollTop, setScrollTop] = useState(0);

  const sorted = useMemo(() => {
    if (!sort.key) return rows;
    const col = columns.find((c) => c.key === sort.key);
    if (!col) return rows;
    const val = col.sortValue || ((r) => r[sort.key]);
    const factor = sort.dir === 'asc' ? 1 : -1;
    return [...rows].sort((a, b) => {
      const av = val(a); const bv = val(b);
      if (av == null) return 1; if (bv == null) return -1;
      return (av > bv ? 1 : av < bv ? -1 : 0) * factor;
    });
  }, [rows, sort, columns]);

  const virtualize = sorted.length > virtualizeThreshold;
  const total = sorted.length * rowHeight;
  const viewport = maxHeight;
  const start = virtualize ? Math.max(0, Math.floor(scrollTop / rowHeight) - 5) : 0;
  const count = virtualize ? Math.ceil(viewport / rowHeight) + 10 : sorted.length;
  const visible = sorted.slice(start, start + count);
  const padTop = start * rowHeight;
  const padBottom = virtualize ? Math.max(0, total - padTop - visible.length * rowHeight) : 0;

  const toggleSort = (key) => setSort((s) =>
    s.key === key ? { key, dir: s.dir === 'asc' ? 'desc' : 'asc' } : { key, dir: 'asc' });

  if (!rows.length) {
    return <div className="ui-table-wrap"><div className="ui-state">{emptyLabel}</div></div>;
  }

  return (
    <div className="ui-table-wrap" style={{ maxHeight }} onScroll={(e) => virtualize && setScrollTop(e.currentTarget.scrollTop)}>
      <table className="ui-table">
        <thead>
          <tr>
            {columns.map((c) => {
              const ariaSort = sort.key === c.key ? (sort.dir === 'asc' ? 'ascending' : 'descending') : (c.sortable ? 'none' : undefined);
              return (
                <th key={c.key} style={{ width: c.width }} aria-sort={ariaSort}
                  onClick={c.sortable ? () => toggleSort(c.key) : undefined}>
                  {c.header}{sort.key === c.key ? (sort.dir === 'asc' ? ' ▲' : ' ▼') : ''}
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {padTop > 0 && <tr style={{ height: padTop }} aria-hidden="true"><td colSpan={columns.length} /></tr>}
          {visible.map((row) => {
            const k = rowKey(row);
            const isNew = newRowKeys?.has(k);
            return (
              <tr key={k} className={isNew ? 'ui-row--new' : undefined} style={virtualize ? { height: rowHeight } : undefined}>
                {columns.map((c) => <td key={c.key}>{c.render ? c.render(row) : row[c.key]}</td>)}
              </tr>
            );
          })}
          {padBottom > 0 && <tr style={{ height: padBottom }} aria-hidden="true"><td colSpan={columns.length} /></tr>}
        </tbody>
      </table>
    </div>
  );
}
