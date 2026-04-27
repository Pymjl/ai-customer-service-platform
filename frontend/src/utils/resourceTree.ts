import type { Key } from 'treemate'
import type { ResourceItem, ResourceTreeNode } from '@/types/system'

const httpMethods = new Set(['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'ANY'])

export interface ResourceDisplayNode extends ResourceTreeNode {
  type: 'service' | 'controller' | 'api' | string
  serviceName?: string
  controllerName?: string
  controllerPath?: string
  apiCount?: number
  disabled?: boolean
  children?: ResourceDisplayNode[]
}

function isApiNode(node?: ResourceTreeNode | null) {
  return node?.type?.toString().toLowerCase() === 'api'
}

function normalizeMethod(method?: string | null) {
  return (method || 'ANY').toUpperCase()
}

function methodFromText(value?: string | null) {
  const match = String(value || '').trim().match(/^(GET|POST|PUT|PATCH|DELETE|ANY)\b\s*/i)
  return match ? match[1].toUpperCase() : ''
}

function controllerNameFromPath(path?: string | null) {
  if (!path) return 'Controller'
  const normalized = path.replace(/\\/g, '/')
  const filename = normalized.substring(normalized.lastIndexOf('/') + 1)
  return filename.endsWith('.java') ? filename.substring(0, filename.length - '.java'.length) : filename
}

function collectApis(nodes: ResourceTreeNode[], serviceName = '', controllerName = '', controllerPath = '') {
  const apis: Array<{
    node: ResourceTreeNode
    resource: ResourceItem
    serviceName: string
    controllerName: string
    controllerPath: string
  }> = []

  const walk = (items: ResourceTreeNode[], currentService = serviceName, currentController = controllerName, currentControllerPath = controllerPath) => {
    items.forEach((item) => {
      const type = item.type?.toString().toLowerCase()
      const nextService = type === 'service' ? item.label : currentService
      const nextController = type === 'controller' ? item.label : currentController
      const nextControllerPath = type === 'controller' ? currentControllerPath : currentControllerPath

      if (isApiNode(item) && item.resource) {
        const resource = item.resource
        const resolvedControllerPath = resource.controllerPath || nextControllerPath
        apis.push({
          node: item,
          resource,
          serviceName: resource.serviceName || nextService || 'unknown-service',
          controllerName: resource.controllerName || nextController || controllerNameFromPath(resolvedControllerPath),
          controllerPath: resolvedControllerPath || ''
        })
      }

      if (item.children?.length) {
        walk(item.children, nextService, nextController, nextControllerPath)
      }
    })
  }

  walk(nodes)
  return apis
}

export function buildResourceDisplayTree(nodes: ResourceTreeNode[]): ResourceDisplayNode[] {
  const serviceMap = new Map<string, ResourceDisplayNode>()

  collectApis(nodes).forEach(({ node, resource, serviceName, controllerName, controllerPath }) => {
    const serviceNode = serviceMap.get(serviceName) || {
      id: `service:${serviceName}`,
      label: serviceName,
      type: 'service',
      serviceName,
      apiCount: 0,
      children: []
    }
    serviceMap.set(serviceName, serviceNode)

    const controllerKey = `controller:${serviceName}:${controllerPath || controllerName}`
    let controllerNode = serviceNode.children?.find((child) => String(child.id) === controllerKey)
    if (!controllerNode) {
      controllerNode = {
        id: controllerKey,
        label: controllerName,
        type: 'controller',
        serviceName,
        controllerName,
        controllerPath,
        apiCount: 0,
        children: []
      }
      serviceNode.children ||= []
      serviceNode.children.push(controllerNode)
    }

    serviceNode.apiCount = (serviceNode.apiCount || 0) + 1
    controllerNode.apiCount = (controllerNode.apiCount || 0) + 1
    controllerNode.children ||= []
    controllerNode.children.push({
      ...node,
      id: node.id,
      label: resource.path || node.label || resource.methodName || 'API',
      type: 'api',
      serviceName,
      controllerName,
      controllerPath,
      resource: {
        ...resource,
        httpMethod: normalizeMethod(resource.httpMethod)
      }
    })
  })

  return [...serviceMap.values()].map((service) => ({
    ...service,
    children: [...(service.children || [])]
      .sort((left, right) => left.label.localeCompare(right.label))
      .map((controller) => ({
        ...controller,
        children: [...(controller.children || [])].sort((left, right) => {
          const leftMethod = left.resource?.httpMethod || ''
          const rightMethod = right.resource?.httpMethod || ''
          return `${left.label}:${leftMethod}`.localeCompare(`${right.label}:${rightMethod}`)
        })
      }))
  }))
}

export function isDisplayApiNode(node?: ResourceTreeNode | null) {
  return isApiNode(node)
}

export function isDisabledApiNode(node?: ResourceTreeNode | null) {
  return isDisplayApiNode(node) && node?.resource?.enabled === false
}

export function markDisabledResourceNodes(nodes: ResourceDisplayNode[]): ResourceDisplayNode[] {
  return nodes.map((node) => ({
    ...node,
    disabled: isDisabledApiNode(node),
    children: node.children ? markDisabledResourceNodes(node.children) : undefined
  }))
}

export function collectTreeKeys(nodes: ResourceTreeNode[]): Key[] {
  const keys: Key[] = []
  const walk = (items: ResourceTreeNode[]) => {
    items.forEach((item) => {
      keys.push(item.id)
      if (item.children?.length) walk(item.children)
    })
  }
  walk(nodes)
  return keys
}

export function collectEnabledApiIds(nodes: ResourceTreeNode[]) {
  const ids: Array<string | number> = []
  const walk = (items: ResourceTreeNode[]) => {
    items.forEach((item) => {
      if (isDisplayApiNode(item) && !isDisabledApiNode(item)) ids.push(item.id)
      if (item.children?.length) walk(item.children)
    })
  }
  walk(nodes)
  return ids
}

export function findResourceNode(nodes: ResourceTreeNode[], key: Key): ResourceTreeNode | null {
  for (const node of nodes) {
    if (String(node.id) === String(key)) return node
    const child = findResourceNode(node.children || [], key)
    if (child) return child
  }
  return null
}

export function firstApiNode(nodes: ResourceTreeNode[]): ResourceTreeNode | null {
  for (const node of nodes) {
    if (isDisplayApiNode(node)) return node
    const child = firstApiNode(node.children || [])
    if (child) return child
  }
  return null
}

export function countResourceTree(nodes: ResourceTreeNode[]) {
  const result = { services: 0, controllers: 0, apis: 0 }
  const walk = (items: ResourceTreeNode[]) => {
    items.forEach((item) => {
      const type = item.type.toString().toLowerCase()
      if (type === 'service') result.services += 1
      if (type === 'controller') result.controllers += 1
      if (type === 'api') result.apis += 1
      if (item.children?.length) walk(item.children)
    })
  }
  walk(nodes)
  return result
}

export function countMatchedApis(nodes: ResourceTreeNode[], pattern: string) {
  const keyword = pattern.trim()
  if (!keyword) return countResourceTree(nodes).apis

  const walk = (items: ResourceTreeNode[], ancestorMatched = false): number => {
    return items.reduce((total, item) => {
      const matched = ancestorMatched || resourceTreeFilter(keyword, item)
      if (isDisplayApiNode(item)) return total + (matched ? 1 : 0)
      return total + walk(item.children || [], matched)
    }, 0)
  }

  return walk(nodes)
}

export function resourceTreeFilter(pattern: string, node: ResourceTreeNode) {
  const keyword = pattern.trim().toLowerCase()
  if (!keyword) return true
  const displayNode = node as ResourceDisplayNode
  const resource = node.resource
  return [
    node.label,
    displayNode.serviceName,
    displayNode.controllerName,
    displayNode.controllerPath,
    resource?.serviceName,
    resource?.controllerName,
    resource?.controllerPath,
    resource?.resourceCode,
    resource?.httpMethod,
    resource?.path,
    resource?.methodName,
    resource?.description
  ].some((value) => String(value || '').toLowerCase().includes(keyword))
}

export function packagePathFromControllerPath(path?: string | null) {
  if (!path) return ''
  const normalized = path.replace(/\\/g, '/')
  const index = normalized.lastIndexOf('/')
  return index > 0 ? normalized.substring(0, index).replace(/\//g, '.') : ''
}

export function apiDisplayMethod(node: ResourceTreeNode) {
  const method = normalizeMethod(node.resource?.httpMethod)
  if (httpMethods.has(method)) return method
  return methodFromText(node.resource?.path) || methodFromText(node.label) || 'ANY'
}

export function apiDisplayPath(node: ResourceTreeNode) {
  const method = apiDisplayMethod(node)
  const rawPath = String(node.resource?.path || node.label || '').trim()
  const withoutMethod = rawPath
    .replace(new RegExp(`^${method}\\b\\s*`, 'i'), '')
    .replace(new RegExp(`^${method}(?=/)`, 'i'), '')
    .trim()
  return withoutMethod || rawPath || '-'
}
