import { describe, expect, it } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ThemeProvider } from '../theme/ThemeProvider.jsx';
import CommandPalette from './CommandPalette.jsx';

const LINKS = [
  { to: '/dashboard', label: 'Dashboard', icon: '▦' },
  { to: '/incidents', label: 'Incidents', icon: '✸' },
];

const renderPalette = () => render(
  <MemoryRouter>
    <ThemeProvider>
      <CommandPalette open onClose={() => {}} links={LINKS} />
    </ThemeProvider>
  </MemoryRouter>,
);

describe('CommandPalette', () => {
  it('lists navigation commands and filters by query', () => {
    renderPalette();
    expect(screen.getByText('Go to Dashboard')).toBeInTheDocument();
    const input = screen.getByRole('combobox');
    fireEvent.change(input, { target: { value: 'incid' } });
    expect(screen.getByText('Go to Incidents')).toBeInTheDocument();
    expect(screen.queryByText('Go to Dashboard')).not.toBeInTheDocument();
  });

  it('offers an "open incident #N" command when a number is typed', () => {
    renderPalette();
    const input = screen.getByRole('combobox');
    fireEvent.change(input, { target: { value: '42' } });
    expect(screen.getByText('Open incident #42')).toBeInTheDocument();
  });
});
