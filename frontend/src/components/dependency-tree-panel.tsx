import { AlertTriangle, ChevronDown, ChevronRight, GitBranch, Search, ShieldAlert, Waypoints } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { buildDependencyTreeIndex, collectNodePathIds, dependencyCoordinateKey, dependencyKey, formatDependencyCoordinate } from "@/lib/dependency-tree";
import type { DependencyTree, DependencyTreeNode, VulnerabilityFinding } from "@/lib/types";
import { cn } from "@/lib/utils";

interface DependencyTreePanelProps {
  tree: DependencyTree;
  dependencyTreeText?: string | null;
  vulnerabilityIndex: Map<string, DependencyVulnerabilitySummary>;
  focusRequest: DependencyTreeFocusRequest | null;
}

export interface DependencyTreeFocusRequest {
  groupId: string | null;
  artifactId: string | null;
  version: string | null;
  token: number;
}

export interface DependencyVulnerabilitySummary {
  count: number;
  vulnerabilities: VulnerabilityFinding[];
}

interface FilteredTreeNode {
  node: DependencyTreeNode;
  children: FilteredTreeNode[];
  searchMatch: boolean;
}

export function DependencyTreePanel({ tree, dependencyTreeText, vulnerabilityIndex, focusRequest }: DependencyTreePanelProps) {
  const index = useMemo(() => buildDependencyTreeIndex(tree), [tree]);
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(tree.roots[0]?.id ?? null);
  const [searchQuery, setSearchQuery] = useState("");
  const [scopeFilter, setScopeFilter] = useState<string>("ALL");
  const [relationFilter, setRelationFilter] = useState<"ALL" | "DIRECT" | "TRANSITIVE">("ALL");
  const [expandedIds, setExpandedIds] = useState<Set<string>>(new Set(tree.roots.map((root) => root.id)));

  useEffect(() => {
    setSelectedNodeId(tree.roots[0]?.id ?? null);
    setExpandedIds(new Set(tree.roots.map((root) => root.id)));
  }, [tree]);

  useEffect(() => {
    if (!focusRequest) {
      return;
    }
    const exactKey = dependencyCoordinateKey(focusRequest);
    const byExact = index.byCoordinate.get(exactKey) ?? [];
    const byArtifact = index.byKey.get(dependencyKey(focusRequest)) ?? [];
    const target = byExact[0] ?? byArtifact[0];
    if (!target) {
      return;
    }
    setSelectedNodeId(target.id);
    const pathIds = collectNodePathIds(target.id, index);
    setExpandedIds((current) => new Set([...current, ...pathIds]));
  }, [focusRequest, index]);

  const availableScopes = useMemo(
    () => Array.from(new Set(index.allNodes.map((node) => node.scope).filter((scope): scope is string => Boolean(scope)))).sort(),
    [index.allNodes],
  );

  const filteredTree = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();

    const visit = (node: DependencyTreeNode): FilteredTreeNode | null => {
      const searchMatch =
        query.length === 0 ||
        [node.groupId, node.artifactId, node.version, node.scope, node.type]
          .filter(Boolean)
          .join(":")
          .toLowerCase()
          .includes(query);
      const scopeMatch = node.depth === 0 || scopeFilter === "ALL" || node.scope === scopeFilter;
      const relationMatch =
        node.depth === 0 ||
        relationFilter === "ALL" ||
        (relationFilter === "DIRECT" && node.direct) ||
        (relationFilter === "TRANSITIVE" && node.transitive);

      const children = node.children
        .map((child) => visit(child))
        .filter((child): child is FilteredTreeNode => child !== null);
      const visible = (scopeMatch && relationMatch && searchMatch) || children.length > 0;

      if (!visible) {
        return null;
      }

      return { node, children, searchMatch };
    };

    return tree.roots
      .map((root) => visit(root))
      .filter((root): root is FilteredTreeNode => root !== null);
  }, [relationFilter, scopeFilter, searchQuery, tree.roots]);

  const visibleNodeCount = useMemo(() => {
    const countNodes = (nodes: FilteredTreeNode[]): number => nodes.reduce((sum, item) => sum + 1 + countNodes(item.children), 0);
    return countNodes(filteredTree);
  }, [filteredTree]);

  const selectedNode = selectedNodeId ? index.byId.get(selectedNodeId) ?? null : null;
  const selectedDependencyKey = selectedNode ? dependencyKey(selectedNode) : null;
  const selectedParent = selectedNode?.parentId ? index.parentById.get(selectedNode.parentId) ?? null : null;
  const selectedPaths = selectedDependencyKey ? index.byKey.get(selectedDependencyKey) ?? [] : [];
  const selectedVulnerabilities = selectedNode ? vulnerabilityIndex.get(dependencyCoordinateKey(selectedNode)) : undefined;
  const forceExpand = searchQuery.trim().length > 0 || scopeFilter !== "ALL" || relationFilter !== "ALL";

  const expandAll = () => setExpandedIds(new Set(index.expandableIds));
  const collapseAll = () => setExpandedIds(new Set(tree.roots.map((root) => root.id)));

  const toggleExpanded = (nodeId: string) => {
    setExpandedIds((current) => {
      const next = new Set(current);
      if (next.has(nodeId)) {
        next.delete(nodeId);
      } else {
        next.add(nodeId);
      }
      return next;
    });
  };

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
        <div>
          <h3 className="text-lg font-semibold">Dependency Tree</h3>
          <p className="text-sm text-muted-foreground">
            Parsed Maven dependency graph with {visibleNodeCount} visible node{visibleNodeCount === 1 ? "" : "s"}. Search and filters keep parent branches visible so paths stay understandable.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button type="button" variant="outline" size="sm" onClick={expandAll}>Expand all</Button>
          <Button type="button" variant="outline" size="sm" onClick={collapseAll}>Collapse all</Button>
        </div>
      </div>

      <div className="grid gap-3 xl:grid-cols-[minmax(0,1.4fr)_minmax(320px,0.9fr)]">
        <Card>
          <CardHeader className="space-y-4">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-center">
              <label className="flex min-w-0 flex-1 items-center gap-2 rounded-full border border-border bg-background px-4 py-2">
                <Search className="h-4 w-4 text-muted-foreground" />
                <input
                  value={searchQuery}
                  onChange={(event) => setSearchQuery(event.target.value)}
                  className="min-w-0 flex-1 bg-transparent text-sm outline-none placeholder:text-muted-foreground"
                  placeholder="Search groupId, artifactId, version, scope"
                />
              </label>
              <select
                value={scopeFilter}
                onChange={(event) => setScopeFilter(event.target.value)}
                className="rounded-full border border-border bg-background px-4 py-2 text-sm outline-none"
              >
                <option value="ALL">All scopes</option>
                {availableScopes.map((scope) => (
                  <option key={scope} value={scope}>{scope}</option>
                ))}
              </select>
              <select
                value={relationFilter}
                onChange={(event) => setRelationFilter(event.target.value as "ALL" | "DIRECT" | "TRANSITIVE")}
                className="rounded-full border border-border bg-background px-4 py-2 text-sm outline-none"
              >
                <option value="ALL">Direct + transitive</option>
                <option value="DIRECT">Direct only</option>
                <option value="TRANSITIVE">Transitive only</option>
              </select>
            </div>
          </CardHeader>
          <CardContent>
            <div className="max-h-[720px] space-y-2 overflow-auto pr-1">
              {filteredTree.map((root) => (
                <DependencyTreeRow
                  key={root.node.id}
                  view={root}
                  selectedNodeId={selectedNodeId}
                  expandedIds={expandedIds}
                  forceExpand={forceExpand}
                  vulnerabilityIndex={vulnerabilityIndex}
                  onSelect={setSelectedNodeId}
                  onToggleExpanded={toggleExpanded}
                />
              ))}
              {filteredTree.length === 0 ? (
                <div className="rounded-3xl border border-dashed border-border/80 bg-background/70 px-5 py-8 text-sm text-muted-foreground">
                  No dependencies matched the current search and filter combination.
                </div>
              ) : null}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>{selectedNode ? selectedNode.artifactId ?? "Selected dependency" : "Dependency details"}</CardTitle>
            <CardDescription>
              Click a dependency node to inspect its coordinates, parent edge, path from the root project, and any mapped vulnerability context.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-6">
            {selectedNode ? (
              <>
                <div className="grid gap-3 sm:grid-cols-2">
                  {[
                    ["groupId", selectedNode.groupId ?? "unknown"],
                    ["artifactId", selectedNode.artifactId ?? "unknown"],
                    ["version", selectedNode.version ?? "unknown"],
                    ["scope", selectedNode.scope ?? "n/a"],
                    ["dependency kind", selectedNode.direct ? "Direct" : selectedNode.transitive ? "Transitive" : "Root project"],
                    ["parent dependency", selectedParent ? formatDependencyCoordinate(selectedParent) : "Root project"],
                  ].map(([label, value]) => (
                    <div key={label as string} className="rounded-2xl border border-border/70 bg-background/70 px-4 py-4">
                      <div className="text-xs uppercase tracking-[0.2em] text-muted-foreground">{label}</div>
                      <div className="mt-2 break-all text-sm font-medium">{value}</div>
                    </div>
                  ))}
                </div>

                <div className="space-y-3">
                  <div className="flex items-center gap-2 text-sm font-medium">
                    <Waypoints className="h-4 w-4" /> Path from root
                  </div>
                  <div className="rounded-3xl border border-border/70 bg-background/70 px-4 py-4">
                    <div className="space-y-2 text-sm">
                      {selectedNode.pathFromRoot.map((step, index) => (
                        <div key={`${step}-${index}`} className="flex items-start gap-2">
                          <span className="mt-0.5 text-xs text-muted-foreground">{index === 0 ? "root" : `#${index}`}</span>
                          <span className="break-all">{step}</span>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>

                <div className="space-y-3">
                  <div className="flex items-center gap-2 text-sm font-medium">
                    <GitBranch className="h-4 w-4" /> Why is this dependency here?
                  </div>
                  <div className="space-y-3">
                    {selectedPaths.map((pathNode) => (
                      <div key={pathNode.id} className="rounded-3xl border border-border/70 bg-background/70 px-4 py-4">
                        <div className="space-y-2 text-sm">
                          {pathNode.pathFromRoot.map((step, index) => (
                            <div key={`${pathNode.id}-${step}-${index}`} className="flex items-start gap-2">
                              <span className="mt-0.5 text-xs text-muted-foreground">{index === 0 ? "project" : "└──"}</span>
                              <span className="break-all">{step}</span>
                            </div>
                          ))}
                        </div>
                      </div>
                    ))}
                    {selectedPaths.length === 0 ? (
                      <div className="rounded-3xl border border-dashed border-border/80 bg-background/70 px-4 py-5 text-sm text-muted-foreground">
                        No path data was available for this dependency key.
                      </div>
                    ) : null}
                  </div>
                </div>

                <div className="space-y-3">
                  <div className="flex items-center gap-2 text-sm font-medium">
                    <ShieldAlert className="h-4 w-4" /> Vulnerabilities
                  </div>
                  <div className="rounded-3xl border border-border/70 bg-background/70 px-4 py-4 text-sm">
                    {selectedVulnerabilities && selectedVulnerabilities.vulnerabilities.length > 0 ? (
                      <div className="space-y-2">
                        <div className="text-muted-foreground">{selectedVulnerabilities.count} mapped finding{selectedVulnerabilities.count === 1 ? "" : "s"}</div>
                        {selectedVulnerabilities.vulnerabilities.slice(0, 8).map((finding, index) => (
                          <div key={`${finding.cveId ?? "finding"}-${index}`} className="rounded-2xl border border-border/70 bg-background px-3 py-3">
                            <div className="flex flex-wrap items-center gap-2">
                              <Badge variant={severityVariant(finding.severity)}>{finding.severity}</Badge>
                              <span className="font-medium">{finding.cveId ?? finding.packageName ?? "Unnamed finding"}</span>
                            </div>
                            <div className="mt-2 text-muted-foreground">{finding.description ?? "No description available."}</div>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <div className="text-muted-foreground">No mapped vulnerability findings for this dependency node.</div>
                    )}
                  </div>
                </div>

                <div className="grid gap-3 md:grid-cols-2">
                  <PlaceholderPanel title="Licenses">See the Licenses tab for dependency license evidence and category warnings.</PlaceholderPanel>
                  <PlaceholderPanel title="Usage status">See the Usage Analysis tab for evidence-based usage status, confidence, warnings, and slimming guidance.</PlaceholderPanel>
                </div>

                {selectedNode.conflict || selectedNode.omitted ? (
                  <div className="rounded-3xl border border-amber-500/30 bg-amber-500/10 px-4 py-4 text-sm text-amber-900 dark:text-amber-100">
                    <div className="font-medium">Conflict and omission details</div>
                    <div className="mt-2">
                      {selectedNode.omittedReason ?? "This node was marked as omitted or conflicting in Maven output."}
                    </div>
                  </div>
                ) : null}
              </>
            ) : (
              <div className="rounded-3xl border border-dashed border-border/80 bg-background/70 px-4 py-6 text-sm text-muted-foreground">
                Select a dependency to inspect its details.
              </div>
            )}

            {dependencyTreeText ? (
              <details className="rounded-3xl border border-border/70 bg-background/70 px-4 py-4 text-sm">
                <summary className="cursor-pointer font-medium">Raw Maven dependency tree output</summary>
                <pre className="mt-4 overflow-x-auto rounded-2xl bg-slate-950 p-4 text-xs text-slate-200">
                  {dependencyTreeText}
                </pre>
              </details>
            ) : null}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function DependencyTreeRow({
  view,
  selectedNodeId,
  expandedIds,
  forceExpand,
  vulnerabilityIndex,
  onSelect,
  onToggleExpanded,
}: {
  view: FilteredTreeNode;
  selectedNodeId: string | null;
  expandedIds: Set<string>;
  forceExpand: boolean;
  vulnerabilityIndex: Map<string, DependencyVulnerabilitySummary>;
  onSelect: (nodeId: string) => void;
  onToggleExpanded: (nodeId: string) => void;
}) {
  const { node, children, searchMatch } = view;
  const vulnerabilitySummary = vulnerabilityIndex.get(dependencyCoordinateKey(node));
  const isExpanded = forceExpand || expandedIds.has(node.id);
  const isSelected = selectedNodeId === node.id;
  const hasChildren = children.length > 0;

  return (
    <div>
      <div
        className={cn(
          "flex items-start gap-3 rounded-3xl border px-4 py-3 transition",
          isSelected ? "border-primary bg-primary/5 shadow-sm" : "border-border/70 bg-background/70 hover:border-primary/40 hover:bg-background",
          searchMatch ? "ring-1 ring-primary/20" : undefined,
          node.conflict ? "border-amber-500/40" : undefined,
          node.omitted ? "border-dashed" : undefined,
          vulnerabilitySummary?.count ? "shadow-[0_0_0_1px_rgba(244,63,94,0.12)]" : undefined,
        )}
        style={{ marginLeft: `${node.depth * 1.1}rem` }}
      >
        <div className="mt-0.5 flex h-6 w-6 items-center justify-center rounded-full border border-border/70 bg-background text-muted-foreground">
          {hasChildren ? (
            <button
              type="button"
              aria-label={`Toggle ${node.artifactId ?? node.id}`}
              className="flex h-full w-full items-center justify-center"
              onClick={() => onToggleExpanded(node.id)}
            >
              {isExpanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
            </button>
          ) : (
            <span className="text-xs">•</span>
          )}
        </div>
        <button type="button" onClick={() => onSelect(node.id)} className="min-w-0 flex-1 space-y-2 text-left">
          <div className="flex min-w-0 flex-wrap items-center gap-2">
            <span className="truncate font-medium">{node.artifactId ?? "unknown-artifact"}</span>
            <span className="text-sm text-muted-foreground">{node.version ?? "unknown"}</span>
            {node.depth === 0 ? <Badge variant="neutral">root project</Badge> : null}
            {node.direct ? <Badge variant="info">direct</Badge> : null}
            {node.transitive ? <Badge variant="neutral">transitive</Badge> : null}
            {node.scope ? <Badge variant="neutral">{node.scope}</Badge> : null}
            {vulnerabilitySummary?.count ? <Badge variant="critical">{vulnerabilitySummary.count} vulnerabilities</Badge> : null}
            {node.conflict ? <Badge variant="medium">conflict</Badge> : null}
            {node.omitted ? <Badge variant="neutral">omitted</Badge> : null}
          </div>
          <div className="break-all text-xs text-muted-foreground">{formatDependencyCoordinate(node)}</div>
          {node.omittedReason ? <div className="text-xs text-amber-700 dark:text-amber-300">{node.omittedReason}</div> : null}
        </button>
        {vulnerabilitySummary?.count ? <AlertTriangle className="mt-1 h-4 w-4 text-rose-500" /> : null}
      </div>

      {hasChildren && isExpanded ? (
        <div className="mt-2 space-y-2">
          {children.map((child) => (
            <DependencyTreeRow
              key={child.node.id}
              view={child}
              selectedNodeId={selectedNodeId}
              expandedIds={expandedIds}
              forceExpand={forceExpand}
              vulnerabilityIndex={vulnerabilityIndex}
              onSelect={onSelect}
              onToggleExpanded={onToggleExpanded}
            />
          ))}
        </div>
      ) : null}
    </div>
  );
}

function PlaceholderPanel({ title, children }: { title: string; children: string }) {
  return (
    <div className="rounded-3xl border border-border/70 bg-background/70 px-4 py-4 text-sm">
      <div className="font-medium">{title}</div>
      <div className="mt-2 text-muted-foreground">{children}</div>
    </div>
  );
}

function severityVariant(severity: VulnerabilityFinding["severity"]) {
  switch (severity) {
    case "CRITICAL":
      return "critical" as const;
    case "HIGH":
      return "high" as const;
    case "MEDIUM":
      return "medium" as const;
    case "LOW":
      return "low" as const;
    case "INFO":
      return "info" as const;
    default:
      return "neutral" as const;
  }
}
