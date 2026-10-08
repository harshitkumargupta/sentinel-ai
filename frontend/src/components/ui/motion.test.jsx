import { describe, expect, it, beforeEach, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import TextField from './TextField.jsx';
import Button from './Button.jsx';
import OnboardingTour from '../OnboardingTour.jsx';

describe('TextField', () => {
  it('renders a labelled input and shows an error with aria-invalid', () => {
    render(<TextField label="Username" error="Required" value="" onChange={() => {}} />);
    expect(screen.getByText('Username')).toBeInTheDocument();
    const input = screen.getByRole('textbox');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('alert')).toHaveTextContent('Required');
  });
});

describe('Button motion states', () => {
  it('disables and marks busy while loading, and does not fire onClick', () => {
    const onClick = vi.fn();
    render(<Button loading onClick={onClick}>Save</Button>);
    const btn = screen.getByRole('button');
    expect(btn).toBeDisabled();
    fireEvent.click(btn);
    expect(onClick).not.toHaveBeenCalled();
  });
});

describe('OnboardingTour', () => {
  beforeEach(() => { try { localStorage.clear(); } catch { /* ignore */ } });

  it('shows on first run and remembers after skip', () => {
    const { unmount } = render(<OnboardingTour />);
    expect(screen.getByRole('dialog', { name: /getting started/i })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /skip/i }));
    expect(localStorage.getItem('sentinel.onboarded')).toBe('1');
    unmount();
    render(<OnboardingTour />);
    expect(screen.queryByRole('dialog', { name: /getting started/i })).not.toBeInTheDocument();
  });
});
