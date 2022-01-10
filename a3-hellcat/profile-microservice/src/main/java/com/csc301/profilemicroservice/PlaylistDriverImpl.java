package com.csc301.profilemicroservice;

import static org.neo4j.driver.v1.Values.parameters;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.neo4j.driver.v1.Transaction;

@Repository
public class PlaylistDriverImpl implements PlaylistDriver {

	Driver driver = ProfileMicroserviceApplication.driver;

	public static void InitPlaylistDb() {
		String queryStr;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				queryStr = "CREATE CONSTRAINT ON (nPlaylist:playlist) ASSERT exists(nPlaylist.plName)";
				trans.run(queryStr);
				trans.success();
			}
			session.close();
		}
	}

	@Override
	public DbQueryStatus likeSong(String userName, String songId) {
		DbQueryStatus dbq;
		if (!userNameExisted(userName)) {
			dbq = new DbQueryStatus("User not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
        	return dbq;
		}
		
		if (!songExistedMongo(songId)) {
			dbq = new DbQueryStatus("Song not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
        	return dbq;
		}
		
		if (!songExistedNeo4j(songId)) {
			try (Session session = driver.session()){
				try (Transaction tx = session.beginTransaction()){
					tx.run("CREATE (:song{songId:$songId})", parameters("songId", songId));
					tx.success();
				}
				session.close();
			}catch(Exception e) {
	        	dbq = new DbQueryStatus("Failed to create song node", DbQueryExecResult.QUERY_ERROR_GENERIC);
	        	return dbq;
	        }
		}
		
		if(!hasLiked(userName, songId)) {
			try (Session session = driver.session()){
				try (Transaction tx = session.beginTransaction()){
					tx.run("MATCH (s:song),(l:playlist) WHERE (l.plName CONTAINS $userName AND s.songId=$songId) CREATE (l)-[:includes]->(s)"
							, parameters("songId", songId, "userName", userName));
					tx.success();
				}
				session.close();
			}catch(Exception e) {
	        	dbq = new DbQueryStatus("Failed to add song to user's playlist", DbQueryExecResult.QUERY_ERROR_GENERIC);
	        	return dbq;
	        }
			updateSonglikeMongo(songId, "false");
		}
		dbq = new DbQueryStatus("Song liked", DbQueryExecResult.QUERY_OK);
		return dbq;
	}

	@Override
	public DbQueryStatus unlikeSong(String userName, String songId) {
		DbQueryStatus dbq;
		if (!userNameExisted(userName)) {
			dbq = new DbQueryStatus("User not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
        	return dbq;
		}
		
		if (!songExistedMongo(songId)) {
			dbq = new DbQueryStatus("Song not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
        	return dbq;
		}
		
		if(!hasLiked(userName, songId)) {
			dbq = new DbQueryStatus("User never like this song", DbQueryExecResult.QUERY_ERROR_GENERIC);
        	return dbq;
		}
		
		if (!songExistedNeo4j(songId)) {
			try (Session session = driver.session()){
				try (Transaction tx = session.beginTransaction()){
					tx.run("CREATE (:song{songId:$songId})", parameters("songId", songId));
					tx.success();
				}
				session.close();
			}catch(Exception e) {
	        	dbq = new DbQueryStatus("Failed to create song node", DbQueryExecResult.QUERY_ERROR_GENERIC);
	        	return dbq;
	        }
		}
		
		try (Session session = driver.session()){
			try (Transaction tx = session.beginTransaction()){
				tx.run("MATCH (:playlist {plName:$userName})-[r:includes]->(:song{songId:$songId}) DELETE r"
						, parameters("songId", songId, "userName", userName + "-favoritesplaylist"));
				tx.success();
			}
			session.close();
		}catch(Exception e) {
        	dbq = new DbQueryStatus("Failed to remove song from user's playlist", DbQueryExecResult.QUERY_ERROR_GENERIC);
        	return dbq;
        }
		updateSonglikeMongo(songId, "true");
		dbq = new DbQueryStatus("Song unliked", DbQueryExecResult.QUERY_OK);
		return dbq;
	}

	@Override
	public DbQueryStatus deleteSongFromDb(String songId) {
		DbQueryStatus dbq;
		if (songExistedNeo4j(songId)) {
			try (Session session = driver.session()){
				try (Transaction tx = session.beginTransaction()){
					tx.run("MATCH ()-[r:includes]->(s{songId:$songId}) DELETE r", parameters("songId", songId));
					tx.run("MATCH (s:song {songId:$songId}) DELETE s", parameters("songId", songId));
					tx.success();
				}
				session.close();
			}catch(Exception e) {
	        	dbq = new DbQueryStatus("Failed to remove song from DB", DbQueryExecResult.QUERY_ERROR_GENERIC);
	        	return dbq;
	        }
		}
		
		if (songExistedMongo(songId)) {
			OkHttpClient client = new OkHttpClient();
			ObjectMapper mapper = new ObjectMapper();
			Map<String, Object> response = new HashMap<String, Object>();
			
			String url = "http://localhost:3001/deleteSongById/" + songId;
			Request request = new Request.Builder()
					.url(url)
					.method("DELETE", null)
					.build();
			
			Call call = client.newCall(request);
			Response responseFromsongService = null;
			
			String songServiceBody = "{}";
			
			try {
				responseFromsongService = call.execute();
				songServiceBody = responseFromsongService.body().string();
				response = mapper.readValue(songServiceBody, Map.class);
				if(response.get("status").equals("OK")) {
					dbq = new DbQueryStatus("Song deleted", DbQueryExecResult.QUERY_OK);
				}else {
					dbq = new DbQueryStatus("Error deleting song", DbQueryExecResult.QUERY_ERROR_GENERIC);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		dbq = new DbQueryStatus("Song deleted", DbQueryExecResult.QUERY_OK);
		return dbq;
	}
	
	private boolean userNameExisted(String userName) {
		StatementResult exisitence;
		try (Session session = driver.session()){
			try (Transaction tx = session.beginTransaction()){
				exisitence = tx.run("MATCH (p:profile{userName:$userName}) RETURN p", parameters("userName", userName));
				tx.success();
				if(exisitence.hasNext()) {
					session.close();
	            	return true;
				}      
			}
			session.close();
		}
		return false;
	}
	
	private boolean songExistedMongo(String songId) {
		OkHttpClient client = new OkHttpClient();
		ObjectMapper mapper = new ObjectMapper();
		Map<String, Object> response = new HashMap<String, Object>();
		
		String url = "http://localhost:3001/getSongTitleById/" + songId;
		Request request = new Request.Builder()
				.url(url)
				.method("GET", null)
				.build();
		
		Call call = client.newCall(request);
		Response responseFromsongService = null;
		
		String songServiceBody = "{}";
		
		try {
			responseFromsongService = call.execute();
			songServiceBody = responseFromsongService.body().string();
			response = mapper.readValue(songServiceBody, Map.class);
			//System.out.println(response);
			//response = (Map<String, Object>) response.get("data");
			//System.out.println(response.get("status"));
			if(response.get("status").equals("OK")) {
				return true;
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}
	
	private boolean songExistedNeo4j(String songId) {
		StatementResult exisitence;
		try (Session session = driver.session()){
			try (Transaction tx = session.beginTransaction()){
				exisitence = tx.run("MATCH (s:song{songId:$songId}) RETURN s", parameters("songId", songId));
				tx.success();
				if(exisitence.hasNext()) {
					session.close();
	            	return true;
				}      
			}
			session.close();
		}
		return false;
	}
	
	private void updateSonglikeMongo(String songId, String shouldDecrement) {
		OkHttpClient client = new OkHttpClient();
		
		String url = "http://localhost:3001/updateSongFavouritesCount/" + songId;
		HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();
		urlBuilder.addQueryParameter("shouldDecrement", shouldDecrement);
		url = urlBuilder.build().toString();
		RequestBody body = RequestBody.create(null, new byte[0]);
		Request request = new Request.Builder()
				.url(url)
				.method("PUT", body)
				.build();
		
		Call call = client.newCall(request);
		Response responseFromsongService = null;
		
		try {
			responseFromsongService = call.execute();
		}catch (IOException e) {
			e.printStackTrace();
		}
		responseFromsongService.close();
	}
	
	private boolean hasLiked(String userName, String songId) {
		StatementResult exisitence;
		
		try (Session session = driver.session()){
			try (Transaction tx = session.beginTransaction()){
				exisitence = tx.run("MATCH p=(:playlist {plName:$userName})-[:includes]->(:song{songId:$songId}) RETURN p", 
	    				parameters("userName", userName + "-favoritesplaylist", "songId", songId));
				tx.success();
				if(exisitence.hasNext()) {
					session.close();
	            	return true;
				}      
			}
			session.close();
		}
		return false;
	}
}
