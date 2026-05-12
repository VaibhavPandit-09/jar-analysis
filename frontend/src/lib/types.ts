export type JobStatus = "QUEUED" | "RUNNING" | "COMPLETED" | "FAILED" | "CANCELLED";
export type InputType = "ARCHIVE_UPLOAD" | "POM" | "PROJECT_ZIP";
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
  | "EXTRACTING_PROJECT_ZIP"
  | "DETECTING_PROJECT_STRUCTURE"
  | "MAVEN_RESOLUTION"
  | "ANALYZING"
  | "ANALYZING_PACKAGED_ARTIFACTS"
  | "INSPECTING_WAR_EAR"
  | "INSPECTING_FAT_JAR"
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
  packagingInspection: PackagingInspection | null;
}

export interface NestedLibrarySummary {
  fileName: string;
  sizeBytes: number;
  javaVersion: string;
  vulnerabilityCount: number;
  coordinates: MavenCoordinates;
}

export interface PackagingInspection {
  packagingType: string;
  applicationClassesLocation: string | null;
  dependencyLibrariesLocation: string | null;
  applicationClassCount: number;
  dependencyCount: number;
  nestedLibraryCount: number;
  nestedLibraries: NestedLibrarySummary[];
  largestNestedLibraries: NestedLibrarySummary[];
  vulnerableNestedLibraries: NestedLibrarySummary[];
  javaVersion: string;
  springBootVersion: string | null;
  startClass: string | null;
  mainClass: string | null;
  layersIndexPresent: boolean;
  classpathIndexPresent: boolean;
  webXmlPresent: boolean;
  applicationXmlPresent: boolean;
  webInfLibCount: number;
  warModuleCount: number;
  jarModuleCount: number;
  modulePaths: string[];
  springMetadataFiles: string[];
  serviceLoaderFiles: string[];
  configFiles: string[];
  duplicateClassesStatus: string;
}

export interface ProjectStructureSummary {
  archiveName: string;
  rootPomPath: string | null;
  rootPomReason: string | null;
  pomCount: number;
  packagedArtifactCount: number;
  compiledClassDirectoryCount: number;
  dependencyLibraryDirectoryCount: number;
  pomFiles: string[];
  moduleDirectories: string[];
  compiledClassDirectories: string[];
  packagedArtifacts: string[];
  dependencyLibraryDirectories: string[];
  springMetadataFiles: string[];
  serviceLoaderFiles: string[];
  resourceFiles: string[];
  compiledClassesJavaVersion: JavaVersionInfo;
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
  projectStructure: ProjectStructureSummary | null;
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
  cliVersion: string | null;
  dataDirectory: string | null;
  apiKeyConfigured: boolean;
  lastUpdated: string | null;
  isUpdating: boolean;
  lastSyncStartedAt: string | null;
  lastSyncCompletedAt: string | null;
  lastSyncDurationMs: number | null;
  lastSyncStatus: string | null;
  lastSyncError: string | null;
  message: string;
}

export interface NvdSettingsStatus {
  configured: boolean;
  maskedKey: string | null;
  storageMode: string;
  updatedAt: string | null;
}

export interface NvdSettingsTestResponse {
  configured: boolean;
  valid: boolean;
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

export type DependencyChangeType = "ADDED" | "REMOVED" | "UPDATED" | "UNCHANGED";
export type VulnerabilityChangeType = "NEW" | "FIXED" | "CHANGED" | "UNCHANGED";

export interface CountDiff {
  before: number;
  after: number;
  delta: number;
}

export interface DoubleDiff {
  before: number | null;
  after: number | null;
  delta: number | null;
}

export interface ScanComparisonSummaryDiff {
  totalArtifacts: CountDiff;
  totalDependencies: CountDiff;
  totalVulnerabilities: CountDiff;
  critical: CountDiff;
  high: CountDiff;
  medium: CountDiff;
  low: CountDiff;
  highestCvss: DoubleDiff;
  beforePolicyStatus: string | null;
  afterPolicyStatus: string | null;
  licenseCount: CountDiff | null;
}

export interface DependencyChangeItem {
  changeType: DependencyChangeType;
  artifactKey: string;
  oldGroupId: string | null;
  oldArtifactId: string | null;
  newGroupId: string | null;
  newArtifactId: string | null;
  oldVersion: string | null;
  newVersion: string | null;
  oldJavaVersion: string | null;
  newJavaVersion: string | null;
  oldVulnerabilityCount: number | null;
  newVulnerabilityCount: number | null;
  scope: string | null;
  coordinatesChanged: boolean;
  versionChanged: boolean;
  javaVersionChanged: boolean;
  vulnerabilityCountChanged: boolean;
}

export interface VulnerabilityChangeItem {
  changeType: VulnerabilityChangeType;
  vulnerabilityId: string;
  cveId: string | null;
  oldSeverity: Severity | null;
  newSeverity: Severity | null;
  oldCvss: number | null;
  newCvss: number | null;
  dependencyKey: string;
  dependencyGroupId: string | null;
  dependencyArtifactId: string | null;
  oldDependencyVersion: string | null;
  newDependencyVersion: string | null;
}

export interface DependencyComparisonSection {
  addedCount: number;
  removedCount: number;
  updatedCount: number;
  unchangedCount: number;
  changes: DependencyChangeItem[];
}

export interface VulnerabilityComparisonSection {
  newCount: number;
  fixedCount: number;
  changedCount: number;
  unchangedCount: number;
  changes: VulnerabilityChangeItem[];
}

export interface ScanComparisonResponse {
  baseline: StoredScanSummary;
  target: StoredScanSummary;
  summaryDiff: ScanComparisonSummaryDiff;
  dependencyChanges: DependencyComparisonSection;
  vulnerabilityChanges: VulnerabilityComparisonSection;
  warnings: string[];
  errors: string[];
}
