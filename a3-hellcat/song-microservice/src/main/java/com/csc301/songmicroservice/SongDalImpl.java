package com.csc301.songmicroservice;

import static com.mongodb.client.model.Filters.eq;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;


import com.mongodb.client.MongoCollection;

@Repository
public class SongDalImpl implements SongDal {

	private final MongoTemplate db;

	@Autowired
	public SongDalImpl(MongoTemplate mongoTemplate) {
		this.db = mongoTemplate;
	}

	@Override
	public DbQueryStatus addSong(Song songToAdd) {
		MongoCollection col = db.getDb().getCollection("songs");
		
		Document filter = new Document();
		Document title = new Document().append("$eq", songToAdd.getSongName());
		Document artist = new Document().append("$eq", songToAdd.getSongArtistFullName());
		filter.append("songName", title);
		filter.append("songArtistFullName", artist);
		Document result = (Document) col.find(filter).first();
		if(result!=null) {
			DbQueryStatus dbq = new DbQueryStatus("Song existed", DbQueryExecResult.QUERY_ERROR_GENERIC);
			return dbq;
		}
		
		Document newSong = new Document();
		newSong.put("songName", songToAdd.getSongName());
		newSong.put("songArtistFullName", songToAdd.getSongArtistFullName());
		newSong.put("songAlbum", songToAdd.getSongAlbum());
		newSong.put("songAmountFavourites", songToAdd.getSongAmountFavourites());
		try {
			col.insertOne(newSong);
		}catch(Exception e) {
			DbQueryStatus dbq = new DbQueryStatus("Song added failed", DbQueryExecResult.QUERY_ERROR_GENERIC);
			return dbq;
		}
		ObjectId id = (ObjectId) newSong.get("_id");
		songToAdd.setId(id);
		DbQueryStatus dbq = new DbQueryStatus("Song added successfully", DbQueryExecResult.QUERY_OK);
		dbq.setData(id);
		return dbq;
	}

	@Override
	public DbQueryStatus findSongById(String songId) {
		MongoCollection col = db.getDb().getCollection("songs");
		Document result = (Document) col.find(eq("_id", new ObjectId(songId))).first();
		if (result==null) {
			DbQueryStatus dbq = new DbQueryStatus("Song not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
			return dbq;
		}
		DbQueryStatus dbq = new DbQueryStatus("Song found", DbQueryExecResult.QUERY_OK);
		String songName = result.getString("songName");
		String artist = result.getString("songArtistFullName");
		String album = result.getString("songAlbum");
		long favourites = (long) result.get("songAmountFavourites");
		Song song = new Song(songName, artist, album);
		song.setSongAmountFavourites(favourites);
		song.setId(result.getObjectId("_id"));
		dbq.setData(song);
		return dbq;
	}

	@Override
	public DbQueryStatus getSongTitleById(String songId) {
		MongoCollection col = db.getDb().getCollection("songs");
		Document result = (Document) col.find(eq("_id", new ObjectId(songId))).first();
		if (result==null) {
			DbQueryStatus dbq = new DbQueryStatus("Song not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
			return dbq;
		}
		DbQueryStatus dbq = new DbQueryStatus("Song found", DbQueryExecResult.QUERY_OK);
		dbq.setData(result.get("songName"));
		return dbq;
	}

	@Override
	public DbQueryStatus deleteSongById(String songId) {
		//Automatically check if id is valid
		MongoCollection col = db.getDb().getCollection("songs");
		Document result = (Document) col.findOneAndDelete(eq("_id", new ObjectId(songId)));
		if (result==null) {
			DbQueryStatus dbq = new DbQueryStatus("Song not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
			return dbq;
		}
		DbQueryStatus dbq = new DbQueryStatus("Song found and deleted", DbQueryExecResult.QUERY_OK);
		return dbq;
	}

	@Override
	public DbQueryStatus updateSongFavouritesCount(String songId, boolean shouldDecrement) {
		MongoCollection col = db.getDb().getCollection("songs");
		Document result;
		if (shouldDecrement) {
			Document update = new Document(new Document().append("$inc", new Document().append("songAmountFavourites", -1)));
			result = (Document) col.findOneAndUpdate(eq("_id", new ObjectId(songId)), update);
		}else {
			Document update = new Document(new Document().append("$inc", new Document().append("songAmountFavourites", 1)));
			result = (Document) col.findOneAndUpdate(eq("_id", new ObjectId(songId)), update);
		}
		
		if (result==null) {
			DbQueryStatus dbq = new DbQueryStatus("Song not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
			return dbq;
		}
		DbQueryStatus dbq = new DbQueryStatus("songAmountFavourites updated", DbQueryExecResult.QUERY_OK);
		return dbq;
	}
}