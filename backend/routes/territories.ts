import { Router } from 'express';
import {
  getTerritoryLeaderboard,
  getTerritoryMetrics
} from '../controllers/territoryController';

const router = Router();

router.get('/leaderboard', getTerritoryLeaderboard);
router.get('/:territoryId/metrics', getTerritoryMetrics);

export default router;
