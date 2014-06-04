package net.jiaoqsh.rsm;

import java.io.IOException;

import net.jiaoqsh.rsm.redis.JedisTemplate;
import net.jiaoqsh.rsm.redis.JedisUtils;
import net.jiaoqsh.rsm.serializer.Serializer;

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.Session;
import org.apache.catalina.session.ManagerBase;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;


public class RedisSessionManager extends ManagerBase{
	
	protected final Log logger = LogFactory.getLog( getClass() );
	
	// -------------------- configuration properties begin--------------------
	private String debug = "false"; 
	protected String host = JedisUtils.DEFAULT_HOST;
	protected int port = JedisUtils.DEFAULT_PORT;
	protected int database = JedisUtils.DEFAULT_DATABASE;
	protected String password = null;
	protected int timeout = JedisUtils.DEFAULT_TIMEOUT;
	// -------------------- configuration properties end--------------------
	protected JedisPool jedisPool;
	protected JedisTemplate jedisTemplate;
	
	protected Serializer serializer;
	protected String serializationStrategyClass = "net.jiaoqsh.rsm.serializer.JavaSerializer";
	
	protected LifecycleSupport lifecycle = new LifecycleSupport(this);
	
	
	private void initJedis(){
		logger.info("init redis, host: "+ getHost()); 
		
		jedisPool = new JedisPool(new JedisPoolConfig(), getHost(), getPort(), getTimeout(), getPassword());
		jedisTemplate = new JedisTemplate(jedisPool);
	}
	
	// ----------------------------------------------------- Instance Variables
    /**
     * The descriptive information about this implementation.
     */
    private static final String info = "RedisSessionManager/1.1";
    /**
     * The descriptive name of this Manager implementation (for logging).
     */
    private static String name = "RedisSessionManager";
    
    /**
     * Add a lifecycle event listener to this component.
     *
     * @param listener The listener to add
     */
    @Override
    public void addLifecycleListener(LifecycleListener listener) {
      lifecycle.addLifecycleListener(listener);
    }

    /**
     * Get the lifecycle listeners associated with this lifecycle. If this
     * Lifecycle has no listeners registered, a zero-length array is returned.
     */
    @Override
    public LifecycleListener[] findLifecycleListeners() {
      return lifecycle.findLifecycleListeners();
    }
    
	@Override
	public void load() throws ClassNotFoundException, IOException {
	}

	@Override
	public void unload() throws IOException {
	}
	
	// -------------------- Override begin--------------------
	
	 /**
     * Start this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {
        super.startInternal();
        setState(LifecycleState.STARTING);
        try {
            initializeSerializer();
          } catch (ClassNotFoundException e) {
        	  logger.fatal("Unable to load serializer", e);
        	  throw new LifecycleException(e);
          } catch (InstantiationException e) {
        	  logger.fatal("Unable to load serializer", e);
        	  throw new LifecycleException(e);
          } catch (IllegalAccessException e) {
        	  logger.fatal("Unable to load serializer", e);
        	  throw new LifecycleException(e);
          }
        initJedis();
    }


    /**
     * Stop this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        if (logger.isDebugEnabled())
        	logger.debug("Stopping");

        setState(LifecycleState.STOPPING);
        
        jedisPool.destroy();

        // Require a new random number generator if we are restarted
        super.stopInternal();
    }
	
	
	@Override
    public void processExpires() {
      // We are going to use Redis's ability to expire keys for session expiration.

      // Do nothing.
    }
	
	   /**
     * Construct and return a new session object, based on the default
     * settings specified by this Manager's properties.  The session
     * id specified will be used as the session id.  
     * If a new session cannot be created for any reason, return 
     * <code>null</code>.
     * 
     * @param sessionId The session id which should be used to create the
     *  new session; if <code>null</code>, a new session id will be
     *  generated
     * @exception IllegalStateException if a new session cannot be
     *  instantiated for any reason
     */
    @Override
    public Session createSession(String sessionId) {
        
        // Recycle or create a Session instance
    	RedisSession session = (RedisSession)createEmptySession();

        // Initialize the properties of the new session and return it
        session.setNew(true);
        session.setValid(true);
        session.setCreationTime(System.currentTimeMillis());
        session.setMaxInactiveInterval(this.maxInactiveInterval);

        String id = sessionId;
        do{
	        if (id == null) {
	            id = generateSessionId();
	        }
        }while(jedisTemplate.hsetnxex(id, "id", id, this.maxInactiveInterval));
        logger.info("create session, id :"+ id);
        
        session.setId(id);       
        
        return (session);

    }
    
    
    /**
     * Get a session from the recycled ones or create a new empty one.
     * The PersistentManager manager does not need to create session data
     * because it reads it from the Store.
     */
    @Override
    public Session createEmptySession() {
        return new RedisSession(this);
    }
	
	
    /**
     * Return the active Session, associated with this Manager, with the
     * specified session id (if any); otherwise return <code>null</code>.
     * This method checks the persistence store if persistence is enabled,
     * otherwise just uses the functionality from ManagerBase.
     *
     * @param id The session id for the session to be returned
     *
     * @exception IllegalStateException if a new session cannot be
     *  instantiated for any reason
     * @exception IOException if an input/output error occurs while
     *  processing this request
     */
    @Override
    public Session findSession(String id) throws IOException {
/*        Session session = super.findSession(id);
        if (session != null)
            return (session);*/
        // See if the Session is in the Redis
    	Session session = loadSessionFromRedis(id);
        return (session);
    }	
    
    private Session loadSessionFromRedis(String id) throws IOException{
    	logger.info("loadSessionFromRedis id:" + id);
    	
    	if(!jedisTemplate.exists(id)){
    		logger.debug("Session " + id + " not found in Redis");
    		return null;
    	}
    	
    	logger.debug("session " + id + " exists in Redis");
    	RedisSession session = (RedisSession)createEmptySession();
    	session.setCreationTime(System.currentTimeMillis());
        session.setNew(false);
        session.setMaxInactiveInterval(getMaxInactiveInterval());
        session.setValid(true);
        session.setLoadId(id);
        
        //this.add(session);
        //sessionCounter++;
        
        return session;
    }
    
    
    /**
     * Remove this Session from the active Sessions for this Manager.
     *
     * @param session   Session to be removed
     * @param update    Should the expiration statistics be updated
     */
    @Override
    public void remove(Session session, boolean update) {
    	logger.info("Removing session, ID : " + session.getId());
    	
        if (session.getIdInternal() != null) {
            sessions.remove(session.getIdInternal());
        }
        
        jedisTemplate.del(session.getId());
        
    }
    
    
    
	
    private void initializeSerializer() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
    	logger.info("Attempting to use serializer :" + serializationStrategyClass);
        serializer = (Serializer) Class.forName(serializationStrategyClass).newInstance();

        Loader loader = null;

        if (container != null) {
          loader = container.getLoader();
        }

        ClassLoader classLoader = null;

        if (loader != null) {
            classLoader = loader.getClassLoader();
        }
        serializer.setClassLoader(classLoader);
      }

	// -------------------- Override begin--------------------
    
    
	
	public String getDebug() {
		return debug;
	}

	public void setDebug(String debug) {
		this.debug = debug;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getDatabase() {
		return database;
	}

	public void setDatabase(int database) {
		this.database = database;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public JedisPool getJedisPool() {
		return jedisPool;
	}

	public void setJedisPool(JedisPool jedisPool) {
		this.jedisPool = jedisPool;
	}

	public JedisTemplate getJedisTemplate() {
		return jedisTemplate;
	}

	public void setJedisTemplate(JedisTemplate jedisTemplate) {
		this.jedisTemplate = jedisTemplate;
	}

	public LifecycleSupport getLifecycle() {
		return lifecycle;
	}

	public void setLifecycle(LifecycleSupport lifecycle) {
		this.lifecycle = lifecycle;
	}
	
}
