import { describe, expect, it } from 'vitest'
import { ADMIN_SECTIONS } from './adminSections'

describe('admin sections (SCRUM-115)', () => {
  it('does not expose the unbuilt Subscriptions and Reports sections', () => {
    const ids = ADMIN_SECTIONS.map((s) => s.id)
    expect(ids).not.toContain('subscriptions')
    expect(ids).not.toContain('reports')
  })

  it('keeps Users first, since /admin opens on the first section', () => {
    expect(ADMIN_SECTIONS[0].id).toBe('users')
  })
})