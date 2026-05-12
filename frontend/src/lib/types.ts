export type JobStatus = "QUEUED" | "RUNNING" | "COMPLETED" | "FAILED" | "CANCELLED";
export type InputType = "ARCHIVE_UPLOAD" | "POM";
export type Severity = "CRITICAL" | "HIGH" | "MEDIUM" | "LOW" | "INFO" | "UNKNOWN";
export type ProgressEventType =
  | "STARTED"
  | "PROGRESS"
  | "LOG"
  | "WARNING"
  | "ERROR"
  | "COMPLETED"
  | "CANCELLED";
export type ProgressPhase =
  | "PREPARING"
  | "VALIDATING_UPLOAD"
  | "MAVEN_RESOLUTION"
  | "ANALYZING"
  | "VULNERABILITY_SCAN"
  | "REPORTING"
  | "COMPLETED"
  | "FAILED"
  | "CANCELLED";

export interface MavenCoordinates {
  groupId: string | null;
  artifactId: string | null;
  version: string | null;
}

export interface JavaVersionInfo {
  minMajor: number | null;
  maxMajor: number | null;
  requiredJava: string;
  multiRelease: boolean;
}

export interface ManifestInfo {
  mainClass: string | null;
  implementationTitle: string | null;
  implementationVersion: string | null;
  implementationVendor: string | null;
  createdBy: string | null;
  buildJdk: string | null;
  automaticModuleName: string | null;
  multiRelease: string | null;
  attributes: Record<string, string>;
}

export interface VulnerabilityFinding {
  severity: Severity;
  cveId: string | null;
  cvssScore: number | null;
  packageName: string | null;
  installedVersion: string | null;
  affectedVersionRange: string | null;
  description: string | null;
  references: string[];
  source: string | null;
}

export interface DependencyInfo {
  artifact: string;
  coordinates: MavenCoordinates;
  scope: string | null;
  direct: boolean;
  javaVersion: string | null;
  vulnerabilityCount: number;
}

export interface ArtifactAnalysis {
  id: string;
  fileName: string;
  sizeBytes: number;
  sha256: string;
  entryCount: number;
  fatJar: boolean;
  parentPath: string | null;
  nestedDepth: number;
  coordinates: MavenCoordinates;
  javaVersion: JavaVersionInfo;
  manifest: ManifestInfo;
  moduleType: string;
  highestSeverity: Severity;
  vulnerabilityCount: number;
  dependencies: DependencyInfo[];
  vulnerabilities: VulnerabilityFinding[];
  nestedArtifacts: ArtifactAnalysis[];
  rawMetadata: Record<string, unknown>;
}

export interface AnalysisSummary {
  totalArtifacts: number;
  totalDependencies: number;
  vulnerableDependencies: number;
  totalVulnerabilities: number;
  critical: number;
  high: number;
  medium: number;
  low: number;
  info: number;
  unknown: number;
  highestCvss: number | null;
  requiredJavaVersion: string;
}

export interface AnalysisResult {
  jobId: string;
  status: JobStatus;
  inputType: InputType;
  startedAt: string | null;
  completedAt: string | null;
  summary: AnalysisSummary;
  artifacts: ArtifactAnalysis[];
  dependencyTreeText: string | null;
  warnings: string[];
  errors: string[];
}

export interface AnalysisJobStatus {
  jobId: string;
  status: JobStatus;
  inputType: InputType;
  startedAt: string | null;
  completedAt: string | null;
  message: string;
  warnings: string[];
  errors: string[];
}

export interface ProgressEvent {
  jobId: string;
  type: ProgressEventType;
  phase: ProgressPhase;
  message: string;
  percent: number | null;
  currentItem: string | null;
  completedItems: number | null;
  totalItems: number | null;
  timestamp: string;
}

export interface VulnerabilityDbStatus {
  available: boolean;
  lastUpdated: string | null;
  isUpdating: boolean;
  message: string;
}

export interface StoredScanSummary {
  scanId: string;
  jobId: string;
  inputType: InputType;
  inputName: string | null;
  inputHash: string | null;
  status: JobStatus;
  startedAt: string | null;
  completedAt: string | null;
  durationMs: number | null;
  totalArtifacts: number;
  totalDependencies: number;
  totalVulnerabilities: number;
  criticalCount: number;
  highCount: number;
  mediumCount: number;
  lowCount: number;
  infoCount: number;
  unknownCount: number;
  highestCvss: number | null;
  requiredJavaVersion: string | null;
  highestSeverity: Severity;
  createdAppVersion: string | null;
  notes: string | null;
  tags: string[];
  createdAt: string;
  updatedAt: string;
}

export interface StoredScan {
  summary: StoredScanSummary;
  result: AnalysisResult | null;
}

export interface StoredScanQuery {
  q?: string;
  inputType?: InputType;
  status?: JobStatus;
  severity?: Severity;
  sort?: string;
  direction?: "asc" | "desc";
  limit?: number;
  offset?: number;
}

export interface UpdateStoredScanPayload {
  notes?: string | null;
  tags?: string[];
}
