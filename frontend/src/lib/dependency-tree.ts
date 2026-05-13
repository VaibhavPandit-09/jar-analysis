import type { DependencyTree, DependencyTreeNode } from "@/lib/types";

export interface DependencyTreeIndex {
  byId: Map<string, DependencyTreeNode>;
  byKey: Map<string, DependencyTreeNode[]>;
  byCoordinate: Map<string, DependencyTreeNode[]>;
  parentById: Map<string, DependencyTreeNode | null>;
  allNodes: DependencyTreeNode[];
  expandableIds: string[];
}

export function dependencyKey(input: Pick<DependencyTreeNode, "groupId" | "artifactId"> | { groupId: string | null; artifactId: string | null }) {
  return `${input.groupId ?? "unknown"}:${input.artifactId ?? "unknown"}`;
}

export function dependencyCoordinateKey(
  input:
    | Pick<DependencyTreeNode, "groupId" | "artifactId" | "version">
    | { groupId: string | null; artifactId: string | null; version: string | null },
) {
  return `${dependencyKey(input)}:${input.version ?? "unknown"}`;
}

export function formatDependencyCoordinate(node: { groupId: string | null; artifactId: string | null; version: string | null }) {
  return `${node.groupId ?? "unknown"}:${node.artifactId ?? "unknown"}:${node.version ?? "unknown"}`;
}

export function buildDependencyTreeIndex(tree: DependencyTree | null): DependencyTreeIndex {
  const byId = new Map<string, DependencyTreeNode>();
  const byKey = new Map<string, DependencyTreeNode[]>();
  const byCoordinate = new Map<string, DependencyTreeNode[]>();
  const parentById = new Map<string, DependencyTreeNode | null>();
  const allNodes: DependencyTreeNode[] = [];
  const expandableIds: string[] = [];

  const visit = (node: DependencyTreeNode, parent: DependencyTreeNode | null) => {
    byId.set(node.id, node);
    parentById.set(node.id, parent);
    allNodes.push(node);
    if (node.children.length > 0) {
      expandableIds.push(node.id);
    }

    const key = dependencyKey(node);
    const coordinateKey = dependencyCoordinateKey(node);
    byKey.set(key, [...(byKey.get(key) ?? []), node]);
    byCoordinate.set(coordinateKey, [...(byCoordinate.get(coordinateKey) ?? []), node]);

    node.children.forEach((child) => visit(child, node));
  };

  tree?.roots.forEach((root) => visit(root, null));

  return {
    byId,
    byKey,
    byCoordinate,
    parentById,
    allNodes,
    expandableIds,
  };
}

export function collectNodePathIds(nodeId: string, index: DependencyTreeIndex) {
  const path: string[] = [];
  let current = index.byId.get(nodeId) ?? null;
  while (current) {
    path.unshift(current.id);
    current = index.parentById.get(current.id) ?? null;
  }
  return path;
}
