package net.jiaoqsh.rsm;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class RedisSessionService {

	protected final Log logger = LogFactory.getLog( getClass() );
	
	public static final String TOMCAT_SESSION_PREFIX = "tomcat:session:";
	
	// -------------------- configuration properties begin--------------------
	
	private String debug = "false"; 
	
	private String redisNodes;

	public String getDebug() {
		return debug;
	}

	public void setDebug(String debug) {
		this.debug = debug;
	}

	public String getRedisNodes() {
		return redisNodes;
	}

	public void setRedisNodes(String redisNodes) {
		this.redisNodes = redisNodes;
	} 
	
	// -------------------- configuration properties end--------------------
	
	
	
	
}
