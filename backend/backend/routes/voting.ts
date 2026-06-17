import { Router } from 'express';
import {
  castVote,
  getElectionResults
} from '../controllers/votingController';

const router = Router();

router.post('/cast', castVote);
router.get('/results/:electionId', getElectionResults);

export default router;
