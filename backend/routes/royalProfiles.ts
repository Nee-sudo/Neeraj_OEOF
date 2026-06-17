import { Router } from 'express';
import {
  getMonarchProfile,
  getThroneWorthiness,
  getRoyalMemberProfile,
  getImperialHierarchy
} from '../controllers/royalProfileController';

const router = Router();

// Monarch Profile routes
router.get('/monarchs/:monarchId', getMonarchProfile);
router.get('/monarchs/:monarchId/throne-worthiness', getThroneWorthiness);

// Royal Member & Hierarchy routes
router.get('/members/:memberId', getRoyalMemberProfile);
router.get('/hierarchy', getImperialHierarchy);

export default router;
