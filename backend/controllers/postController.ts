import { Request, Response } from 'express';
import { getFirestoreDb } from '../config/database';
import { IPost } from '../models/Post';
import { IComment } from '../models/Comment';
import { getNextSequenceValue } from '../models/Counter';
import { IUser, calculateUserRank } from '../models/User';

export function normalizeBackendPost(raw: any): IPost {
  if (!raw) {
    return {
      id: 0,
      authorId: "anonymous",
      authorName: "Anonymous Citizen",
      authorUsername: "@anonymous",
      authorRank: "Citizen",
      authorTerritory: "Global",
      authorFlag: "🌍",
      content: "",
      category: "General",
      timestamp: Date.now(),
      knowledgeValue: 0,
      contributionProof: 0,
      reputationImpact: 100,
      reactedWiseUsers: "",
      reactedHelpfulUsers: "",
      reactedInspiringUsers: ""
    };
  }

  // 1. Coerce ID to valid number
  let cleanId = 0;
  if (raw.id !== undefined && raw.id !== null) {
    if (typeof raw.id === 'number') {
      cleanId = raw.id;
    } else {
      const parsedStr = String(raw.id).replace(/\D/g, '');
      const parsed = parseInt(parsedStr, 10);
      cleanId = isNaN(parsed) ? 999 + Math.floor(Math.random() * 1000) : parsed;
    }
  } else if (raw._id !== undefined && raw._id !== null) {
    if (typeof raw._id === 'number') {
      cleanId = raw._id;
    } else {
      const parsedStr = String(raw._id).replace(/\D/g, '');
      const parsed = parseInt(parsedStr, 10);
      cleanId = isNaN(parsed) ? 999 + Math.floor(Math.random() * 1000) : parsed;
    }
  }

  // 2. Map snake_case to camelCase
  const authorId = raw.authorId || raw.author_id || "anonymous";
  const authorName = raw.authorName || raw.author_name || "Anonymous Citizen";
  const authorUsername = raw.authorUsername || raw.author_username || "@anonymous";
  const authorRank = raw.authorRank || raw.author_rank || "Citizen";
  const authorTerritory = raw.authorTerritory || raw.author_territory || "Global";
  const authorFlag = raw.authorFlag || raw.author_flag || "🌍";

  const content = raw.content || raw.title || "";
  const category = raw.category || raw.type || "Inquiry";

  // Handle timestamp
  let timestamp = Date.now();
  if (raw.timestamp !== undefined && raw.timestamp !== null) {
    timestamp = Number(raw.timestamp);
  } else if (raw.created_at !== undefined && raw.created_at !== null) {
    const val = Number(raw.created_at);
    if (val < 100000000) {
      timestamp = Date.now() - (100000000 - val) * 1000;
    } else {
      timestamp = val;
    }
  }

  // Metrics
  const knowledgeValue = Number(raw.knowledgeValue !== undefined ? raw.knowledgeValue : (raw.wise_count || 0));
  const contributionProof = Number(raw.contributionProof !== undefined ? raw.contributionProof : (raw.helpful_count || 0));
  
  let reputationImpact = Number(raw.reputationImpact !== undefined ? raw.reputationImpact : 100);
  if (raw.reputationImpact === undefined && raw.inspiring_count !== undefined) {
    reputationImpact = 100 + Number(raw.inspiring_count);
  }

  const reactedWiseUsers = raw.reactedWiseUsers || raw.reacted_wise_users || "";
  const reactedHelpfulUsers = raw.reactedHelpfulUsers || raw.reacted_helpful_users || "";
  const reactedInspiringUsers = raw.reactedInspiringUsers || raw.reacted_inspiring_users || "";

  return {
    id: cleanId,
    authorId,
    authorName,
    authorUsername,
    authorRank,
    authorTerritory,
    authorFlag,
    content,
    category,
    timestamp,
    knowledgeValue,
    contributionProof,
    reputationImpact,
    reactedWiseUsers,
    reactedHelpfulUsers,
    reactedInspiringUsers
  };
}

export const getPosts = async (req: Request, res: Response) => {
  try {
    const { category } = req.query;
    const db = getFirestoreDb();
    
    // Fetch all posts and sort in memory to avoid requiring manual Firestore composite indexes
    const snapshot = await db.collection('posts').get();
    let posts = snapshot.docs.map(doc => normalizeBackendPost(doc.data()));
    
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
    res.status(201).json(normalizeBackendPost(newPost));
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
    const cleanUserId = String(userId).trim();
    const cleanType = String(reactionType).toLowerCase().trim();

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

    let finalPostResult: IPost | null = null;

    // Run custom dynamic atomic Transaction (Bug #5)
    await db.runTransaction(async (transaction) => {
      let postDocRef = db.collection('posts').doc(String(postId));
      let postDoc = await transaction.get(postDocRef);

      if (!postDoc.exists) {
        const query = await db.collection('posts').where('id', '==', Number(postId)).get();
        if (query.empty) {
          throw new Error("Post not found.");
        }
        postDocRef = query.docs[0].ref;
        postDoc = await transaction.get(postDocRef);
      }

      const post = postDoc.data() as IPost;
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
      transaction.set(postDocRef, post);

      // If award is earned, adjust original author credits atomically
      if (isAdded && post.authorId) {
        const authorIdClean = post.authorId.toLowerCase().trim().replace(/^@/, '');
        let authorDocRef = db.collection('users').doc(authorIdClean);
        let authorDoc = await transaction.get(authorDocRef);
        
        if (!authorDoc.exists) {
          // Query by email/username
          const emailQuery = await db.collection('users').where('email', '==', authorIdClean).get();
          if (!emailQuery.empty) {
            authorDocRef = emailQuery.docs[0].ref;
            authorDoc = await transaction.get(authorDocRef);
          } else {
            const uQuery = await db.collection('users').where('username', '==', `@${authorIdClean}`).get();
            if (!uQuery.empty) {
              authorDocRef = uQuery.docs[0].ref;
              authorDoc = await transaction.get(authorDocRef);
            }
          }
        }

        if (authorDoc.exists) {
          const author = authorDoc.data() as IUser;
          author.knowledgeCredits = (author.knowledgeCredits || 0) + creditsAward.kb;
          author.contributionCredits = (author.contributionCredits || 0) + creditsAward.cb;
          author.reputationScore = Math.min(100, (author.reputationScore || 98) + 1);
          author.currentRank = calculateUserRank(author.knowledgeCredits, author.contributionCredits);

          transaction.set(authorDocRef, author);
        }
      }

      finalPostResult = post;
    });

    if (finalPostResult) {
      res.status(200).json(normalizeBackendPost(finalPostResult));
    } else {
      res.status(400).json({ error: "Atomic transaction failed to finalize." });
    }
  } catch (error: any) {
    console.error("Firestore reactToPost Error:", error);
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
