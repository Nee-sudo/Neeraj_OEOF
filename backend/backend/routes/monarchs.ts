import { Router } from 'express';
import {
  getCurrentMonarch,
  postRoyalDecree,
  appointCouncilMember,
  getHallOfMonarchs
} from '../controllers/monarchController';

const router = Router();

router.get('/current', getCurrentMonarch);
router.post('/decrees', postRoyalDecree);
router.post('/council', appointCouncilMember);
router.get('/hall', getHallOfMonarchs);

export default router;
