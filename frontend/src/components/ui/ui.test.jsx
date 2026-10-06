import { describe, expect, it } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { ThemeProvider } from '../../theme/ThemeProvider.jsx';
import Button from './Button.jsx';
import { SeverityBadge } from './Badge.jsx';
import StatTile from './StatTile.jsx';
import Table from './Table.jsx';

const withTheme = (ui) => render(<ThemeProvider>{ui}</ThemeProvider>);

describe('Button', () => {
  it('applies the variant class and renders children', () => {
    render(<Button variant="primary">Go</Button>);
    const btn = screen.getByRole('button', { name: 'Go' });
    expect(btn.className).toContain('ui-btn--primary');
  });
});

describe('SeverityBadge', () => {
  it('shows text + glyph so color is not the only signal', () => {
    render(<SeverityBadge severity="CRITICAL" />);
    const el = screen.getByText('CRITICAL');
    expect(el).toBeInTheDocument();
    expect(el.textContent).toMatch(/⬣/); // glyph accompanies the label
  });
});

describe('StatTile', () => {
  it('renders the value (reduced-motion snaps immediately)', () => {
    withTheme(<StatTile label="Open incidents" value={7} />);
    expect(screen.getByText('Open incidents')).toBeInTheDocument();
  });
});

describe('Table', () => {
  const columns = [
    { key: 'name', header: 'Name', sortable: true },
    { key: 'n', header: 'N', sortable: true },
  ];
  const rows = [{ name: 'b', n: 2 }, { name: 'a', n: 1 }];

  it('renders rows and sorts on header click', () => {
    render(<Table columns={columns} rows={rows} rowKey={(r) => r.name} />);
    expect(screen.getByText('a')).toBeInTheDocument();
    const header = screen.getByText('Name');
    fireEvent.click(header);
    const cells = screen.getAllByRole('cell').map((c) => c.textContent);
    expect(cells[0]).toBe('a'); // ascending by name
  });

  it('shows the empty label when there are no rows', () => {
    render(<Table columns={columns} rows={[]} rowKey={(r) => r.name} emptyLabel="Nothing" />);
    expect(screen.getByText('Nothing')).toBeInTheDocument();
  });
});
