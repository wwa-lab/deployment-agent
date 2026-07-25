import type { DirectoryLink, DirectoryLinkIconKey, DirectoryLinkKind } from '../../types'

import iconAnsible from '../../assets/resource-center/icons/ansible-mark.svg'
import iconArcad from '../../assets/resource-center/icons/arcad.png'
import iconConfluence from '../../assets/resource-center/icons/confluence.svg'
import iconGithub from '../../assets/resource-center/icons/github.svg'
import iconInfosec from '../../assets/resource-center/icons/infosec.svg'
import iconJenkins from '../../assets/resource-center/icons/jenkins.svg'
import iconJira from '../../assets/resource-center/icons/jira.svg'
import iconLearning from '../../assets/resource-center/icons/learning.svg'
import iconPeoplesoft from '../../assets/resource-center/icons/peoplesoft.svg'
import iconVendor from '../../assets/resource-center/icons/vendor.svg'
import iconWwa from '../../assets/resource-center/icons/wwa.svg'

const ICON_ASSETS: Record<DirectoryLinkIconKey, string> = {
  confluence: iconConfluence,
  jira: iconJira,
  github: iconGithub,
  jenkins: iconJenkins,
  ansible: iconAnsible,
  arcad: iconArcad,
  peoplesoft: iconPeoplesoft,
  learning: iconLearning,
  infosec: iconInfosec,
  vendor: iconVendor,
  wwa: iconWwa,
}

/**
 * Brand surface behind official logos. Unified to white so each logo's own
 * brand color reads cleanly (Linear / Vercel / Notion pattern); the 1px
 * subtle border on `.icon.has-brand-icon` keeps the white frame visible on
 * white cards. The surface no longer competes with the logo fill — earlier
 * colored surfaces forced deep navy logos (Confluence, vendor `#172B4D`) to
 * read as "black" against pastel tints, and Ansible's `#111111` surface hid
 * its own logo entirely.
 */
export const ICON_SURFACE: Record<DirectoryLinkIconKey, string> = {
  confluence: '#FFFFFF',
  jira: '#FFFFFF',
  github: '#FFFFFF',
  jenkins: '#FFFFFF',
  ansible: '#FFFFFF',
  arcad: '#FFFFFF',
  peoplesoft: '#FFFFFF',
  learning: '#FFFFFF',
  infosec: '#FFFFFF',
  vendor: '#FFFFFF',
  wwa: '#FFFFFF',
}

export const DIRECTORY_LINK_ICON_KEYS: DirectoryLinkIconKey[] = [
  'confluence',
  'jira',
  'github',
  'jenkins',
  'ansible',
  'arcad',
  'peoplesoft',
  'learning',
  'infosec',
  'vendor',
  'wwa',
]

export const KIND_TONE: Record<DirectoryLinkKind, string> = {
  docs: '#2563eb',
  tool: '#0f766e',
  workspace: '#4f46e5',
  repo: '#24292f',
}

export function resolveDirectoryLinkIconSrc(link: DirectoryLink): string | null {
  const key = link.iconKey
  if (!key) {
    return null
  }
  return ICON_ASSETS[key] ?? null
}

export function resolveDirectoryLinkIconSurface(link: DirectoryLink): string {
  const key = link.iconKey
  if (key && ICON_SURFACE[key]) {
    return ICON_SURFACE[key]
  }
  return KIND_TONE[link.kind]
}

export function linkMark(link: DirectoryLink): string {
  if (link.kind === 'repo') {
    return 'GH'
  }
  const trimmed = link.title.trim()
  if (!trimmed) {
    return '?'
  }
  return trimmed.slice(0, 2).toUpperCase()
}
