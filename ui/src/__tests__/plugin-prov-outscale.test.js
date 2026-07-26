import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useI18nStore } from '@ligoj/host'
import def, { catalogConfiguration } from '../index.js'

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

describe('catalogConfiguration', () => {
  it('exports the provider scoped configuration properties', () => {
    expect(Array.isArray(catalogConfiguration)).toBe(true)
    expect(catalogConfiguration.length).toBeGreaterThan(0)
    for (const property of catalogConfiguration) {
      expect(property.name.startsWith('service:prov:outscale:')).toBe(true)
      expect(['regExp', 'string']).toContain(property.type)
      expect(property).toHaveProperty('default')
      expect(property.key).toBeTruthy()
    }
    // Also reachable through the feature dispatcher used by plugin-prov
    expect(def.feature('catalogConfiguration')).toBe(catalogConfiguration)
  })
})

