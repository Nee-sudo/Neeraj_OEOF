import { Router } from 'express';
import {
  createElection,
  getElection,
  nominateCandidate,
  getCandidates
} from '../controllers/electionController';

const router = Router();

router.post('/', createElection);
router.get('/:electionId', getElection);
router.post('/:electionId/nominations', nominateCandidate);
router.get('/:electionId/candidates', getCandidates);

export default router;
