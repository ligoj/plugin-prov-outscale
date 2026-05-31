import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useI18nStore } from '@ligoj/host'
import def from '../index.js'

beforeEach(() => { setActivePinia(createPinia()) })

describe('plugin-prov-outscale contract', () => {
  it('exposes a valid i18n-only tool manifest', () => {
    expect(def.id).toBe('prov-outscale')
    expect(def.requires).toEqual(['prov'])
    expect(def.routes).toBeUndefined()
    expect(def.meta).toMatchObject({ icon: expect.any(String), color: expect.any(String) })
  })
  it('merges i18n on install', () => {
    const i18n = useI18nStore()
    def.install()
    expect(i18n.t('service:prov:outscale:name')).toBe('Name')
  })
  it('feature() throws for any action (legacy controller was empty)', () => {
    expect(() => def.feature('renderFeatures')).toThrow(/no feature "renderFeatures"/)
  })
})
