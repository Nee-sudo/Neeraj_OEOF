import { Router } from 'express';
import { 
  getPosts, 
  createPost, 
  reactToPost, 
  deletePost, 
  getComments, 
  addComment,
  editComment,
  deleteComment,
  editPost
} from '../controllers/postController';

const router = Router();

router.get('/', getPosts);
router.post('/', createPost);
router.put('/:postId', editPost);
router.put('/:postId/react', reactToPost);
router.post('/:postId/react', reactToPost); // Support both PUT/POST for complete client compatibility
router.delete('/:postId', deletePost);

// Comments relations
router.get('/:postId/comments', getComments);
router.post('/:postId/comments', addComment);
router.put('/:postId/comments/:commentId', editComment);
router.delete('/:postId/comments/:commentId', deleteComment);

export default router;
