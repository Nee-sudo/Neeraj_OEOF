import { Request, Response } from 'express';
import { getFirestoreDb } from '../config/database';
import { IPost } from '../models/Post';
import { IComment } from '../models/Comment';
import { getNextSequenceValue } from '../models/Counter';
import { IUser, calculateUserRank } from '../models/User';

export const getPosts = async (req: Request, res: Response) => {
  try {
    const { category } = req.query;
    const db = getFirestoreDb();
    
    // Fetch all posts and sort in memory to avoid requiring manual Firestore composite indexes
    const snapshot = await db.collection('posts').get();
    let posts = snapshot.docs.map(doc => doc.data() as IPost);
    
    if (category) {
      posts = posts.filter(p => p.category === String(category));
    }
    
    posts.sort((a, b) => b.timestamp - a.timestamp);
    res.status(200).json(posts);
  } catch (error: any) {
    console.error("Firestore getPosts Error:", error);
    res.status(500).json({ error: error.message });
  }
};

export const createPost = async (req: Request, res: Response) => {
  try {
    const postData = req.body;
    const db = getFirestoreDb();
    
    const nextId = await getNextSequenceValue('post_id');
    const newPost: IPost = {
      id: nextId,
      authorId: postData.authorId || "anonymous",
      authorName: postData.authorName || "Anonymous Citizen",
      authorUsername: postData.authorUsername || "@anonymous",
      authorRank: postData.authorRank || "Citizen",
      authorTerritory: postData.authorTerritory || "Global",
      authorFlag: postData.authorFlag || "🌍",
      content: postData.content || "",
      category: postData.category || "General",
      timestamp: Date.now(),
      knowledgeValue: 0,
      contributionProof: 0,
      reputationImpact: Number(postData.reputationImpact) || 100,
      reactedWiseUsers: "",
      reactedHelpfulUsers: "",
      reactedInspiringUsers: ""
    };

    // Store in collection using string of sequential ID as doc key
    await db.collection('posts').doc(String(nextId)).set(newPost);
    res.status(201).json(newPost);
  } catch (error: any) {
    console.error("Firestore createPost Error:", error);
    res.status(500).json({ error: error.message });
  }
};

export const reactToPost = async (req: Request, res: Response) => {
  try {
    const { postId } = req.params;
    const { userId, reactionType } = req.body;
    
    if (!userId || !reactionType) {
       res.status(400).json({ error: "userId and reactionType are required." });
       return;
    }

    const db = getFirestoreDb();
    const postDocRef = db.collection('posts').doc(String(postId));
    let postDoc = await postDocRef.get();

    // If doc ID lookup is missing, search via where query
    if (!postDoc.exists) {
      const query = await db.collection('posts').where('id', '==', Number(postId)).get();
      if (query.empty) {
        res.status(404).json({ error: "Post not found." });
        return;
      }
      postDoc = query.docs[0];
    }

    const post = postDoc.data() as IPost;
    const cleanType = String(reactionType).toLowerCase().trim();
    const cleanUserId = String(userId).trim();

    let reactedField: 'reactedWiseUsers' | 'reactedHelpfulUsers' | 'reactedInspiringUsers' = 'reactedWiseUsers';
    let counterField: 'knowledgeValue' | 'contributionProof' | 'reputationImpact' = 'knowledgeValue';
    let creditsAward = { kb: 0, cb: 0 };

    if (cleanType.includes('wise') || cleanType.includes('🧠')) {
      reactedField = 'reactedWiseUsers';
      counterField = 'knowledgeValue';
      creditsAward = { kb: 2, cb: 0 };
    } else if (cleanType.includes('helpful') || cleanType.includes('🔥') || cleanType.includes('🤝')) {
      reactedField = 'reactedHelpfulUsers';
      counterField = 'contributionProof';
      creditsAward = { kb: 0, cb: 2 };
    } else {
      reactedField = 'reactedInspiringUsers';
      counterField = 'reputationImpact';
      creditsAward = { kb: 1, cb: 1 };
    }

    const currentReactedStr = post[reactedField] || "";
    let reactedList = currentReactedStr.split(',').map(s => s.trim()).filter(Boolean);

    let isAdded = false;
    if (reactedList.includes(cleanUserId)) {
      reactedList = reactedList.filter(id => id !== cleanUserId);
      post[counterField] = Math.max(0, post[counterField] - 1);
    } else {
      reactedList.push(cleanUserId);
      post[counterField] = (post[counterField] || 0) + 1;
      isAdded = true;
    }

    post[reactedField] = reactedList.join(',');
    await postDoc.ref.set(post);

    // If award is earned, adjust original author credits
    if (isAdded && post.authorId) {
      const authorIdClean = post.authorId.toLowerCase().trim().replace(/^@/, '');
      let authorDocRef = db.collection('users').doc(authorIdClean);
      let authorDoc = await authorDocRef.get();
      
      if (!authorDoc.exists) {
        // Query by email/username
        const emailQuery = await db.collection('users').where('email', '==', authorIdClean).get();
        if (!emailQuery.empty) {
          authorDocRef = db.collection('users').doc(emailQuery.docs[0].id);
          authorDoc = await authorDocRef.get();
        } else {
          const uQuery = await db.collection('users').where('username', '==', `@${authorIdClean}`).get();
          if (!uQuery.empty) {
            authorDocRef = db.collection('users').doc(uQuery.docs[0].id);
            authorDoc = await authorDocRef.get();
          }
        }
      }

      if (authorDoc.exists) {
        const author = authorDoc.data() as IUser;
        author.knowledgeCredits = (author.knowledgeCredits || 0) + creditsAward.kb;
        author.contributionCredits = (author.contributionCredits || 0) + creditsAward.cb;
        author.reputationScore = Math.min(100, (author.reputationScore || 98) + 1);
        
        // Dynamic recompute of levels/rank with helper
        const total = (author.knowledgeCredits || 0) + (author.contributionCredits || 0);
        if (total >= 300) {
          author.currentRank = 'Guardian';
        } else if (total >= 150) {
          author.currentRank = 'Contributor';
        } else {
          author.currentRank = 'Citizen';
        }

        await authorDocRef.set(author);
      }
    }

    res.status(200).json(post);
  } catch (error: any) {
    console.error("Firestore reacToPost Error:", error);
    res.status(500).json({ error: error.message });
  }
};

export const deletePost = async (req: Request, res: Response) => {
  try {
    const { postId } = req.params;
    const db = getFirestoreDb();
    
    // Attempt doc delete
    await db.collection('posts').doc(String(postId)).delete();

    // In case doc key is different, query and delete
    const query = await db.collection('posts').where('id', '==', Number(postId)).get();
    for (const doc of query.docs) {
      await doc.ref.delete();
    }

    res.status(200).json({ success: true });
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
};

export const getComments = async (req: Request, res: Response) => {
  try {
    const { postId } = req.params;
    const db = getFirestoreDb();
    
    const snapshot = await db.collection('comments').where('postId', '==', Number(postId)).get();
    const comments = snapshot.docs.map(doc => doc.data() as IComment);
    
    // Sort in memory chronologically
    comments.sort((a, b) => a.timestamp - b.timestamp);
    res.status(200).json(comments);
  } catch (error: any) {
    res.status(500).json({ error: error.message });
  }
};

export const addComment = async (req: Request, res: Response) => {
  try {
    const { postId } = req.params;
    const commentData = req.body;
    const db = getFirestoreDb();
    
    const nextId = await getNextSequenceValue('comment_id');
    const comment: IComment = {
      id: nextId,
      postId: Number(postId),
      authorName: commentData.authorName || "Anonymous",
      authorFlag: commentData.authorFlag || "🌍",
      authorRank: commentData.authorRank || "Citizen",
      content: commentData.content || "",
      timestamp: Date.now()
    };

    await db.collection('comments').doc(String(nextId)).set(comment);
    res.status(201).json(comment);
  } catch (error: any) {
    console.error("Firestore addComment Error:", error);
    res.status(500).json({ error: error.message });
  }
};
