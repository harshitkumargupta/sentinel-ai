import { describe, expect, it, vi } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { ThemeProvider } from '../../theme/ThemeProvider.jsx';
import ThreatCoreLazy from './ThreatCoreLazy.jsx';
import AttackGlobeLazy, { GlobeTable } from './AttackGlobeLazy.jsx';

// jsdom has no WebGL, so the lazy 3D wrappers must fall back to their 2D/static equivalents.
const withTheme = (ui) => render(<ThemeProvider>{ui}</ThemeProvider>);
const flows = [
  { country: 'US', count: 12, topType: 'BRUTE_FORCE' },
  { country: 'IN', count: 4, topType: 'FAILED_LOGIN' },
];

describe('3D fallback logic (no WebGL in jsdom)', () => {
  it('ThreatCoreLazy renders the static orb with an accessible label', () => {
    withTheme(<ThreatCoreLazy level="HIGH" />);
    expect(screen.getByLabelText('Threat level HIGH')).toBeInTheDocument();
  });

  it('AttackGlobeLazy falls back to the static view with the origins table (no procedural globe)', () => {
    withTheme(<AttackGlobeLazy flows={flows} />);
    const table = screen.getByLabelText('Attack origins');
    expect(within(table).getByText('US')).toBeInTheDocument();
    // No <canvas> procedural globe in the fallback.
    expect(document.querySelector('canvas')).toBeNull();
  });

  it('always shows the side panel: top origins, legend and a live/paused toggle', () => {
    withTheme(<AttackGlobeLazy flows={flows} />);
    const panel = screen.getByLabelText('Attack origins summary');
    expect(within(panel).getByText('US')).toBeInTheDocument(); // top origin
    expect(within(screen.getByLabelText('Severity legend')).getByText('CRITICAL')).toBeInTheDocument();
    expect(within(panel).getByRole('button', { name: /Live/i })).toBeInTheDocument();
  });

  it('invokes onSelectCountry when a source is clicked in the fallback table', () => {
    const onSelect = vi.fn();
    withTheme(<AttackGlobeLazy flows={flows} onSelectCountry={onSelect} />);
    const table = screen.getByLabelText('Attack origins');
    fireEvent.click(within(table).getByText('US'));
    expect(onSelect).toHaveBeenCalledWith('US');
  });

  it('GlobeTable shows an empty message with no data', () => {
    render(<GlobeTable flows={[]} />);
    expect(screen.getByText(/No geo-located/i)).toBeInTheDocument();
  });
});
