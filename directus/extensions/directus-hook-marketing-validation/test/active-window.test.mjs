import assert from 'node:assert/strict';
import test from 'node:test';
import { activeWindowError, isWindowCollection } from '../src/active-window.js';

test('accepts open, ordered, and cleared windows', () => {
  assert.equal(activeWindowError({}), '');
  assert.equal(activeWindowError({
    active_from: '2026-08-03T10:00:00.000Z',
    active_to: '2026-08-03T10:00:01.000Z',
  }), '');
  assert.equal(activeWindowError(
    { active_to: null },
    { active_from: '2026-08-03T10:00:00.000Z', active_to: '2026-08-03T09:00:00.000Z' }
  ), '');
});

test('rejects reversed, equal, and malformed windows', () => {
  assert.match(activeWindowError({
    active_from: '2026-08-03T10:00:00.000Z',
    active_to: '2026-08-03T09:00:00.000Z',
  }), /позже начала/);
  assert.match(activeWindowError({
    active_from: '2026-08-03T10:00:00.000Z',
    active_to: '2026-08-03T10:00:00.000Z',
  }), /позже начала/);
  assert.match(activeWindowError({ active_to: 'not-a-date' }), /корректную дату/);
});

test('merges partial updates with the persisted window', () => {
  assert.match(activeWindowError(
    { active_to: '2026-08-03T09:59:59.000Z' },
    { active_from: '2026-08-03T10:00:00.000Z' }
  ), /позже начала/);
  assert.equal(activeWindowError(
    { active_to: '2026-08-03T10:00:01.000Z' },
    { active_from: '2026-08-03T10:00:00.000Z' }
  ), '');
});

test('limits hook validation to campaign and banner', () => {
  assert.equal(isWindowCollection('campaign'), true);
  assert.equal(isWindowCollection('banner'), true);
  assert.equal(isWindowCollection('page'), false);
});
