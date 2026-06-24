export interface DataSet {
  id: string;
  name: string;
  description: string;
  fields: string[];
  owner: string;
  createdAt: string;
  recordCount: number;
  category: 'health' | 'fitness' | 'genomic' | 'lifestyle';
}

export interface ConsentRule {
  id: string;
  datasetId: string;
  allowedRoles: string[];
  allowedPurposes: string[];
  allowedFields: string[];
  deniedFields?: string[];
  validFrom: string;
  validUntil: string;
  status: 'active' | 'revoked' | 'expired';
  createdAt: string;
}

export interface AccessRequest {
  id: string;
  datasetId: string;
  datasetName: string;
  requesterId: string;
  requesterName: string;
  purpose: string;
  requestedFields: string[];
  status: 'pending' | 'approved' | 'rejected' | 'partial';
  requestedAt: string;
  respondedAt?: string;
}

export interface AuditLog {
  id: string;
  timestamp: string;
  userId: string;
  userName: string;
  action: 'consent_created' | 'consent_revoked' | 'data_accessed' | 'request_submitted' | 'request_approved' | 'request_rejected';
  datasetId: string;
  datasetName: string;
  details: string;
}

export interface BillingRecord {
  id: string;
  userId: string;
  userName: string;
  datasetId: string;
  datasetName: string;
  queryCount: number;
  recordsAccessed: number;
  cost: number;
  date: string;
  createdAt?: string;
}

export interface UsageStats {
  totalQueries: number;
  totalRecordsAccessed: number;
  totalCost: number;
  totalRevenue: number;
  monthlyTrend: { month: string; value: number }[];
}
