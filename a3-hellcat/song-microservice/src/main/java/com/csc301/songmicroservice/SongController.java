package com.csc301.songmicroservice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/")
public class SongController {

	@Autowired
	private final SongDal songDal;

	private OkHttpClient client = new OkHttpClient();

	
	public SongController(SongDal songDal) {
		this.songDal = songDal;
	}

	
	@RequestMapping(value = "/getSongById/{songId}", method = RequestMethod.GET)
	public @ResponseBody Map<String, Object> getSongById(@PathVariable("songId") String songId,
			HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("GET %s", Utils.getUrl(request)));

		DbQueryStatus dbQueryStatus = songDal.findSongById(songId);

		response.put("message", dbQueryStatus.getMessage());
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());

		return response;
	}

	
	@RequestMapping(value = "/getSongTitleById/{songId}", method = RequestMethod.GET)
	public @ResponseBody Map<String, Object> getSongTitleById(@PathVariable("songId") String songId,
			HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("GET %s", Utils.getUrl(request)));
		
		DbQueryStatus dbQueryStatus = songDal.getSongTitleById(songId);
//		if(result.getdbQueryExecResult()==DbQueryExecResult.QUERY_ERROR_NOT_FOUND) {
//			response.put("status", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
//			return response;
//		}
//		response.put("data", result.getData());
//		response.put("status", "OK");
		response.put("message", dbQueryStatus.getMessage());
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());

		return response;
	}

	
	@RequestMapping(value = "/deleteSongById/{songId}", method = RequestMethod.DELETE)
	public @ResponseBody Map<String, Object> deleteSongById(@PathVariable("songId") String songId,
			HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("DELETE %s", Utils.getUrl(request)));
		
		DbQueryStatus dbQueryStatus = songDal.deleteSongById(songId);
//		if(result.getdbQueryExecResult()==DbQueryExecResult.QUERY_ERROR_NOT_FOUND) {
//			response.put("status", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
//			return response;
//		}
//		response.put("status", "OK");
		response.put("message", dbQueryStatus.getMessage());
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
		
		return response;
	}

	
	@RequestMapping(value = "/addSong", method = RequestMethod.POST)
	public @ResponseBody Map<String, Object> addSong(@RequestParam Map<String, String> params,
			HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("path", String.format("POST %s", Utils.getUrl(request)));
		
		String songName = params.get("songName");
		String songArtistFullName = params.get("songArtistFullName");
		String songAlbum = params.get("songAlbum");
		if(songName==null || songName.equals("") || songArtistFullName==null || songArtistFullName.equals("")
				|| songAlbum==null || songAlbum.equals("")) {
			response.put("status", HttpStatus.INTERNAL_SERVER_ERROR);
			response.put("message", "Missing parameters");
			return response;
		}
		Song songToAdd = new Song(songName, songArtistFullName, songAlbum);
		DbQueryStatus result = songDal.addSong(songToAdd);
		if(result.getdbQueryExecResult()==DbQueryExecResult.QUERY_ERROR_GENERIC) {
			response.put("status", HttpStatus.INTERNAL_SERVER_ERROR);
			response.put("message", result.getMessage());
			return response;
		}
		response.put("data", songToAdd.getJsonRepresentation());
		response.put("status", HttpStatus.OK);
		response.put("message", result.getMessage());
		return response;
	}

	
	@RequestMapping(value = "/updateSongFavouritesCount/{songId}", method = RequestMethod.PUT)
	public @ResponseBody Map<String, Object> updateFavouritesCount(@PathVariable("songId") String songId,
			@RequestParam("shouldDecrement") String shouldDecrement, HttpServletRequest request) {

		Map<String, Object> response = new HashMap<String, Object>();
		response.put("data", String.format("PUT %s", Utils.getUrl(request)));
		
		DbQueryStatus dbQueryStatus = songDal.updateSongFavouritesCount(songId, Boolean.parseBoolean(shouldDecrement));
//		if(result.getdbQueryExecResult()==DbQueryExecResult.QUERY_ERROR_NOT_FOUND) {
//			response.put("status", DbQueryExecResult.QUERY_ERROR_NOT_FOUND);
//			return response;
//		}
//		response.put("status", "OK");
		response.put("message", dbQueryStatus.getMessage());
		response = Utils.setResponseStatus(response, dbQueryStatus.getdbQueryExecResult(), dbQueryStatus.getData());
		
		return response;
	}
}