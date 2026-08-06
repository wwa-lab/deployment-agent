import { ref } from 'vue'
import type { DirectoryLink, ResourceCenterCatalog } from '../../types'

const STORAGE_KEY = 'wwa.resourceCenter.recent.v1'
const MAX_RECENT = 8

type RecentEntry = {
  linkId: string
  openedAt: string
}

export type ResolvedRecentLink = DirectoryLink & {
  openedAt: string
}

function readEntries(): RecentEntry[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return []
    }
    const parsed = JSON.parse(raw) as unknown
    if (!Array.isArray(parsed)) {
      return []
    }
    return parsed
      .filter(
        (entry): entry is RecentEntry =>
          typeof entry === 'object' &&
          entry != null &&
          typeof (entry as RecentEntry).linkId === 'string' &&
          typeof (entry as RecentEntry).openedAt === 'string',
      )
      .slice(0, MAX_RECENT)
  } catch {
    return []
  }
}

function writeEntries(entries: RecentEntry[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(entries.slice(0, MAX_RECENT)))
  } catch {
    // Ignore quota, security, or private-mode failures.
  }
}

function findLinkById(catalog: ResourceCenterCatalog, linkId: string): DirectoryLink | null {
  for (const scope of catalog.scopes) {
    for (const group of scope.groups) {
      const link = group.links.find((candidate) => candidate.id === linkId)
      if (link) {
        return link
      }
    }
  }
  return null
}

export function useRecentResourceCenterLinks() {
  const entries = ref<RecentEntry[]>(readEntries())

  function record(linkId: string) {
    const next: RecentEntry[] = [
      { linkId, openedAt: new Date().toISOString() },
      ...entries.value.filter((entry) => entry.linkId !== linkId),
    ].slice(0, MAX_RECENT)
    entries.value = next
    writeEntries(next)
  }

  function resolved(catalog: ResourceCenterCatalog | null): ResolvedRecentLink[] {
    if (!catalog) {
      return []
    }
    return entries.value
      .map((entry) => {
        const link = findLinkById(catalog, entry.linkId)
        if (!link) {
          return null
        }
        return { ...link, openedAt: entry.openedAt }
      })
      .filter((link): link is ResolvedRecentLink => link != null)
  }

  function clear() {
    entries.value = []
    try {
      localStorage.removeItem(STORAGE_KEY)
    } catch {
      // Ignore storage failures.
    }
  }

  return {
    record,
    resolved,
    clear,
  }
}
