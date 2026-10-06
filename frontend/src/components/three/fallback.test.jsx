import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ThemeProvider } from '../../theme/ThemeProvider.jsx';
import ThreatCoreLazy from './ThreatCoreLazy.jsx';
import AttackGlobeLazy, { GlobeTable } from './AttackGlobeLazy.jsx';

// jsdom has no WebGL, so the lazy 3D wrappers must fall back to their 2D/static equivalents.
const withTheme = (ui) => render(<ThemeProvider>{ui}</ThemeProvider>);

describe('3D fallback logic (no WebGL in jsdom)', () => {
  it('ThreatCoreLazy renders the static orb with an accessible label', () => {
    withTheme(<ThreatCoreLazy level="HIGH" />);
    expect(screen.getByLabelText('Threat level HIGH')).toBeInTheDocument();
  });

  it('AttackGlobeLazy renders the 2D origins table as the fallback', () => {
    const flows = [{ country: 'US', count: 5, topType: 'FAILED_LOGIN' }];
    withTheme(<AttackGlobeLazy flows={flows} />);
    expect(screen.getByText('US')).toBeInTheDocument();
    expect(screen.getByLabelText('Attack origins')).toBeInTheDocument();
  });

  it('GlobeTable shows an empty message with no data', () => {
    render(<GlobeTable flows={[]} />);
    expect(screen.getByText(/No geo-located/i)).toBeInTheDocument();
  });
});
