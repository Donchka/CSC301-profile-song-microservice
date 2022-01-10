package com.csc301.profilemicroservice;

import static org.neo4j.driver.v1.Values.parameters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.neo4j.driver.v1.Driver;
import org.neo4j.driver.v1.Record;
import org.neo4j.driver.v1.Session;
import org.neo4j.driver.v1.StatementResult;

import org.springframework.stereotype.Repository;

import com.csc301.profilemicroservice.DbQueryExecResult;
import com.csc301.profilemicroservice.DbQueryStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import org.neo4j.driver.v1.Transaction;

@Repository
public class ProfileDriverImpl implements ProfileDriver {

	Driver driver = ProfileMicroserviceApplication.driver;

	public static void InitProfileDb() {
		String queryStr;

		try (Session session = ProfileMicroserviceApplication.driver.session()) {
			try (Transaction trans = session.beginTransaction()) {
				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT exists(nProfile.userName)";
				trans.run(queryStr);

				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT exists(nProfile.password)";
				trans.run(queryStr);

				queryStr = "CREATE CONSTRAINT ON (nProfile:profile) ASSERT nProfile.userName IS UNIQUE";
				trans.run(queryStr);

				trans.success();
			}
			session.close();
		}
	}
	
	@Override
	public DbQueryStatus createUserProfile(String userName, String fullName, String password) {
		DbQueryStatus dbq;
		StatementResult exisitence;
		try (Session session = driver.session()){
			try (Transaction tx = session.beginTransaction()){
				exisitence = tx.run("MATCH (p:profile{fullName:$fullName}) RETURN p", parameters("fullName", fullName));
				if(exisitence.hasNext()) {
					dbq = new DbQueryStatus("Profile duplicated", DbQueryExecResult.QUERY_ERROR_GENERIC);
					session.close();
	            	return dbq;
				}
				exisitence = tx.run("MATCH (p:profile{userName:$userName}) RETURN p", parameters("userName", userName));
				tx.success();
				if(exisitence.hasNext()) {
					dbq = new DbQueryStatus("Profile duplicated", DbQueryExecResult.QUERY_ERROR_GENERIC);
					session.close();
	            	return dbq;
				}
			}catch(Exception e) {
            	dbq = new DbQueryStatus("Profile failed to add", DbQueryExecResult.QUERY_ERROR_GENERIC);
            	return dbq;
            }
			session.close();
		}catch(Exception e) {
        	dbq = new DbQueryStatus("Profile failed to add", DbQueryExecResult.QUERY_ERROR_GENERIC);
        	return dbq;
        }
		try (Session session = driver.session()){
			try (Transaction tx = session.beginTransaction())
            {
        		tx.run("CREATE (:profile{userName:$userName, fullName:$fullName, password:$password})", 
                   	parameters("userName", userName, "fullName", fullName, "password", password));
        		tx.run("CREATE (:playlist{plName:$userNamefavoritesplaylist})", parameters("userNamefavoritesplaylist", userName + "-favoritesplaylist"));
        		tx.run("MATCH (p:profile),(l:playlist) WHERE (l.plName CONTAINS p.userName) CREATE (p)-[:created]->(l)");
                tx.success();
                dbq = new DbQueryStatus("Profile Added", DbQueryExecResult.QUERY_OK);
            }catch(Exception e) {
            	dbq = new DbQueryStatus("Profile failed to add", DbQueryExecResult.QUERY_ERROR_GENERIC);
            	return dbq;
            }
			session.close();
        }catch(Exception e) {
        	dbq = new DbQueryStatus("Profile failed to add", DbQueryExecResult.QUERY_ERROR_GENERIC);
        	return dbq;
        }
		return dbq;
	}

	@Override
	public DbQueryStatus followFriend(String userName, String frndUserName) {
		DbQueryStatus dbq;
		try {
			if(!userNameExisted(userName)) {
				dbq = new DbQueryStatus("User not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
            	return dbq;
			}
			if(!userNameExisted(frndUserName)) {
				dbq = new DbQueryStatus("Friend not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
            	return dbq;
			}
		}catch(Exception e) {
        	dbq = new DbQueryStatus("Failed to follow friend", DbQueryExecResult.QUERY_ERROR_GENERIC);
        	return dbq;
        }
		
		try (Session session = driver.session()){
			try (Transaction tx = session.beginTransaction())
            {
        		tx.run("MATCH (p:profile),(f:profile) WHERE (p.userName=$userName AND f.userName=$frndUserName) CREATE (p)-[:follows]->(f)",
        				parameters("userName", userName, "frndUserName", frndUserName));
                tx.success();
                dbq = new DbQueryStatus("Friend followed", DbQueryExecResult.QUERY_OK);
            }catch(Exception e) {
            	dbq = new DbQueryStatus("Failed to follow friend", DbQueryExecResult.QUERY_ERROR_GENERIC);
            	return dbq;
            }
			session.close();
        }catch(Exception e) {
        	dbq = new DbQueryStatus("Failed to follow friend", DbQueryExecResult.QUERY_ERROR_GENERIC);
        	return dbq;
        }
			
		return dbq;
	}

	@Override
	public DbQueryStatus unfollowFriend(String userName, String frndUserName) {
		DbQueryStatus dbq;
		try {
			if(!userNameExisted(userName)) {
				dbq = new DbQueryStatus("User not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
            	return dbq;
			}
			if(!userNameExisted(frndUserName)) {
				dbq = new DbQueryStatus("Friend not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
            	return dbq;
			}
			if(!hasFollowed(userName, frndUserName)) {
				dbq = new DbQueryStatus("User does not follow this friend", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
            	return dbq;
			}
		}catch(Exception e) {
        	dbq = new DbQueryStatus("Failed to unfollow friend", DbQueryExecResult.QUERY_ERROR_GENERIC);
        	return dbq;
        }
		
		try (Session session = driver.session()){
			try (Transaction tx = session.beginTransaction())
            {
        		tx.run("MATCH (:profile {userName:$userName})-[r:follows]->(:profile{userName:$frndUserName}) DELETE r",
        				parameters("userName", userName, "frndUserName", frndUserName));
                tx.success();
                dbq = new DbQueryStatus("Friend unfollowed", DbQueryExecResult.QUERY_OK);
            }catch(Exception e) {
            	dbq = new DbQueryStatus("Failed to unfollow friend delete", DbQueryExecResult.QUERY_ERROR_GENERIC);
            	return dbq;
            }
			session.close();
        }catch(Exception e) {
        	dbq = new DbQueryStatus("Failed to unfollow friend", DbQueryExecResult.QUERY_ERROR_GENERIC);
        	return dbq;
        }
		
		return dbq;
	}

	@Override
	public DbQueryStatus getAllSongFriendsLike(String userName) {
		DbQueryStatus dbq;
		StatementResult result;
		try {
			if(!userNameExisted(userName)) {
				dbq = new DbQueryStatus("User not found", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
            	return dbq;
			}
		}catch(Exception e) {
        	dbq = new DbQueryStatus("Failed to unfollow friend", DbQueryExecResult.QUERY_ERROR_GENERIC);
        	return dbq;
        }
		
		HashMap <String, List<String>> data = new HashMap<String, List<String>>();
		//List <String> songs = new ArrayList<String>();
		
		try (Session session = driver.session()){
			try (Transaction tx = session.beginTransaction())
            {
        		result = tx.run("MATCH (f:profile) WHERE ((:profile{userName:$userName})-[:follows]->(f)) RETURN f.userName",
        				parameters("userName", userName));
                tx.success();
            }catch(Exception e) {
            	dbq = new DbQueryStatus("Failed to find friends", DbQueryExecResult.QUERY_ERROR_GENERIC);
            	return dbq;
            }
			while(result.hasNext()) {
				data.put(result.next().get("f.userName").asString(), null);
			}
			session.close();
        }catch(Exception e) {
        	dbq = new DbQueryStatus("Failed to find friends", DbQueryExecResult.QUERY_ERROR_GENERIC);
        	return dbq;
        }
		
		List<String> test = new ArrayList<String>();
		test.add("no");
		
		for (String friends:data.keySet()) {
			try (Session session = driver.session()){
				try (Transaction tx = session.beginTransaction())
	            {
	        		result = tx.run("MATCH (s:song) WHERE ((:playlist{plName:$userName})-[:includes]->(s)) RETURN s.songId",
	        				parameters("userName", friends + "-favoritesplaylist"));
	                tx.success();
	            }catch(Exception e) {
	            	dbq = new DbQueryStatus("Failed to find friends' playlists", DbQueryExecResult.QUERY_ERROR_GENERIC);
	            	return dbq;
	            }
				List <String> songs = new ArrayList<String>();
				while(result.hasNext()) {
					songs.add(getsongTitle(result.next().get("s.songId").asString()));
				}
				data.put(friends, songs);
				session.close();
	        }catch(Exception e) {
	        	dbq = new DbQueryStatus("Failed to find friends' playlists", DbQueryExecResult.QUERY_ERROR_GENERIC);
	        	return dbq;
	        }
		}
		dbq = new DbQueryStatus("Retrieved friends' favorite songs", DbQueryExecResult.QUERY_OK);
		dbq.setData(data);
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
	
	private boolean fullNameExisted(String fullName) {
		StatementResult exisitence;
		try (Session session = driver.session()){
			try (Transaction tx = session.beginTransaction()){
				exisitence = tx.run("MATCH (p:profile{fullName:$fullName}) RETURN p", parameters("fullName", fullName));
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
	
	private boolean hasFollowed(String userName, String frndUserName) {
		StatementResult exisitence;
		
		try (Session session = driver.session()){
			try (Transaction tx = session.beginTransaction()){
				exisitence = tx.run("MATCH p=(:profile {userName:$userName})-[:follows]->(:profile{userName:$frndUserName}) RETURN p", 
	    				parameters("userName", userName, "frndUserName", frndUserName));
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
	
	private String getsongTitle(String songId) {
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
		String songName = null;
		
		try {
			responseFromsongService = call.execute();
			songServiceBody = responseFromsongService.body().string();
			response = mapper.readValue(songServiceBody, Map.class);
			//System.out.println(response);
			//response = (Map<String, Object>) response.get("data");
			//System.out.println(response.get("status"));
			songName = (String) response.get("data");
		} catch (IOException e) {
			e.printStackTrace();
		}
		return songName;
	}
}
