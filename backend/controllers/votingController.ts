import { Request, Response } from 'express';
import { VotingService } from '../services/VotingService';
import { getFirestoreDb } from '../config/database';
import { ICandidate } from '../models/Candidate';

export const castVote = async (req: Request, res: Response) => {
  try {
    const { electionId, candidateId } = req.body;
    const voterId = (req as any).userId;
    
    if (!voterId) {
      return res.status(401).json({ error: 'User must be authenticated to vote.' });
    }
    
    if (!electionId || !candidateId) {
      return res.status(400).json({ error: 'Missing electionId or candidateId parameters.' });
    }
    
    const result = await VotingService.castVote(electionId, voterId, candidateId);
    
    if (!result.success) {
      return res.status(400).json({
        success: false,
        error: result.error
      });
    }
    
    return res.status(201).json({
      success: true,
      voteId: result.voteId,
      message: 'Vote successfully recorded on imperial ledger.'
    });
  } catch (error: any) {
    console.error('❌ VotingController castVote error:', error.message);
    return res.status(500).json({ error: 'System fault casting vote.' });
  }
};

export const getElectionResults = async (req: Request, res: Response) => {
  try {
    const { electionId } = req.params;
    const db = getFirestoreDb();
    
    // 1. Resolve candidates participating in this election
    const candSnapshot = await db.collection('candidates')
      .where('electionId', '==', electionId)
      .get();
      
    if (candSnapshot.empty) {
      return res.json({ success: true, results: [], winner: null });
    }
    
    const candidates = candSnapshot.docs.map((doc: any) => doc.data() as ICandidate);
    
    // Sort descending by finalLeadershipScore, then nominatedAt
    candidates.sort((a, b) => {
      const scoreDiff = (b.finalLeadershipScore || 0) - (a.finalLeadershipScore || 0);
      if (scoreDiff !== 0) return scoreDiff;
      return a.nominatedAt - b.nominatedAt;
    });
    
    // Compile mapped rankings
    const results = candidates.map((cand, idx) => {
      return {
        candidateId: cand.candidateId,
        citizenId: cand.citizenId,
        finalLeadershipScore: cand.finalLeadershipScore || 0,
        voteCount: cand.finalVoteCount || 0,
        rank: cand.finalRank || (idx + 1)
      };
    });
    
    return res.json({
      success: true,
      electionId,
      results,
      winner: results[0] || null
    });
  } catch (error: any) {
    console.error('❌ VotingController getElectionResults error:', error.message);
    return res.status(500).json({ error: 'Failed to compile election board results.' });
  }
};
