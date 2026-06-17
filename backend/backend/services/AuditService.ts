import { getFirestoreDb } from '../config/database';
import { IAuditLogEntry, AuditActionType } from '../models/AuditLog';
import crypto from 'crypto';

export async function logAuditEvent(data: {
  actionType: AuditActionType;
  actionCategory: 'ELIGIBILITY' | 'VOTING' | 'SCORING' | 'GOVERNANCE' | 'NOMINATION' | 'RESULT';
  electionId?: string;
  candidateId?: string;
  voterId?: string;
  citizenId?: string;
  details?: any;
  status: 'SUCCESS' | 'FAILURE';
  errorMessage?: string;
}): Promise<void> {
  const db = getFirestoreDb();
  
  const logEntry: IAuditLogEntry = {
    logId: `log_${crypto.randomUUID()}`,
    actionType: data.actionType,
    actionCategory: data.actionCategory,
    electionId: data.electionId,
    candidateId: data.candidateId,
    voterId: data.voterId,
    citizenId: data.citizenId,
    details: data.details || {},
    timestamp: Date.now(),
    initiatedBy: 'SYSTEM',
    status: data.status,
    errorMessage: data.errorMessage,
    createdAt: Date.now()
  };
  
  try {
    // Append-only storage semantic contract (no updates/deletes permitted)
    await db.collection('audit_log').doc(logEntry.logId).set(logEntry);
    console.log(`📝 [Audit] Event '${data.actionType}' logged successfully.`);
  } catch (error: any) {
    console.error('❌ Failed to write audit log entry:', error.message);
  }
}

export async function getAuditLog(electionId: string): Promise<IAuditLogEntry[]> {
  const db = getFirestoreDb();
  try {
    const snapshot = await db.collection('audit_log')
      .where('electionId', '==', electionId)
      .get();
    
    return snapshot.docs.map((doc: any) => doc.data() as IAuditLogEntry);
  } catch {
    return [];
  }
}
