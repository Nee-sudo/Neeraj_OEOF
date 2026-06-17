import { Request, Response } from 'express';
import { getFirestoreDb } from '../config/database';
import { IElection, ElectionState } from '../models/Election';
import { NominationService } from '../services/NominationService';
import { logAuditEvent } from '../services/AuditService';
import crypto from 'crypto';

export const createElection = async (req: Request, res: Response) => {
  try {
    const {
      type,
      territoryId,
      nominationOpensAt,
      nominationClosesAt,
      votingOpensAt,
      votingClosesAt,
      scoringFormulaVersion
    } = req.body;
    
    const userId = (req as any).userId || 'admin';
    
    // Core validation
    if (!type || !nominationOpensAt || !nominationClosesAt || !votingOpensAt || !votingClosesAt) {
      return res.status(400).json({ error: 'Missing required parameters to schedule an election.' });
    }
    
    const db = getFirestoreDb();
    const electionId = `election_${crypto.randomUUID()}`;
    
    const election: IElection = {
      electionId,
      type,
      territoryId,
      state: ElectionState.SCHEDULED,
      nominationOpensAt: Number(nominationOpensAt),
      nominationClosesAt: Number(nominationClosesAt),
      votingOpensAt: Number(votingOpensAt),
      votingClosesAt: Number(votingClosesAt),
      scoringFormulaVersion: scoringFormulaVersion || 'v1',
      voteSaturationCeiling: 100, // Anti-popularity threshold ceiling
      createdAt: Date.now(),
      createdBy: userId,
      updatedAt: Date.now()
    };
    
    await db.collection('elections').doc(electionId).set(election);
    
    await logAuditEvent({
      actionType: 'NOMINATION_OPENED' as any, // initial log type representation
      actionCategory: 'NOMINATION',
      electionId,
      details: { election },
      status: 'SUCCESS'
    });
    
    return res.status(201).json({
      success: true,
      electionId,
      election
    });
  } catch (error: any) {
    console.error('❌ Error creating election:', error.message);
    return res.status(500).json({ error: 'Failed to create election.' });
  }
};

export const getElection = async (req: Request, res: Response) => {
  try {
    const { electionId } = req.params;
    const db = getFirestoreDb();
    
    const electionDoc = await db.collection('elections').doc(electionId).get();
    if (!electionDoc.exists) {
      return res.status(404).json({ error: 'Election not found.' });
    }
    
    return res.json({ success: true, election: electionDoc.data() });
  } catch (error: any) {
    console.error('❌ Error getting election:', error.message);
    return res.status(500).json({ error: 'Failed to retrieve election details.' });
  }
};

export const nominateCandidate = async (req: Request, res: Response) => {
  try {
    const { electionId } = req.params;
    const { visionStatement, manifesto, campaignSpeech, campaignVideoUrl } = req.body;
    const citizenId = (req as any).userId;
    
    if (!citizenId) {
      return res.status(401).json({ error: 'User must be authenticated.' });
    }
    
    const result = await NominationService.nominateCandidate(citizenId, electionId, {
      visionStatement,
      manifesto,
      campaignSpeech,
      campaignVideoUrl
    });
    
    if (!result.success) {
      return res.status(400).json({
        success: false,
        error: result.error,
        eligibilityFailure: result.eligibilityFailure
      });
    }
    
    return res.status(201).json({
      success: true,
      candidateId: result.candidateId,
      message: 'Candidate nominations successfully entered.'
    });
  } catch (error: any) {
    console.error('❌ Error nominating candidate:', error.message);
    return res.status(500).json({ error: 'Nomination procedure failed.' });
  }
};

export const getCandidates = async (req: Request, res: Response) => {
  try {
    const { electionId } = req.params;
    const db = getFirestoreDb();
    
    // Retrieve published candidates (Approved or Published)
    const snapshot = await db.collection('candidates')
      .where('electionId', '==', electionId)
      .get();
      
    const candidates = snapshot.docs.map((doc: any) => doc.data());
    return res.json({ success: true, candidates });
  } catch (error: any) {
    console.error('❌ Error returning candidates list:', error.message);
    return res.status(500).json({ error: 'Failed to retrieve candidates list.' });
  }
};
