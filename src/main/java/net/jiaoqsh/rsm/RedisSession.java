package net.jiaoqsh.rsm;

import java.io.IOException;

import org.apache.catalina.session.StandardSession;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

public class RedisSession extends StandardSession{

	private static final long serialVersionUID = 1L;
	protected transient Log log = LogFactory.getLog( getClass() );
	
	protected transient RedisSessionManager _manager;
	
	public RedisSession(RedisSessionManager manager) {
		super(manager);

		this._manager = manager;
	}
	
	/**
     * Update the accessed time information for this session.  This method
     * should be called by the context when a request comes in for a particular
     * session, even if the application does not reference it.
     */
	@Override
    public void access() {
	   log.info("access id=" + this.id);
       super.access();
       
       _manager.getJedisTemplate().setex(this.id, this.maxInactiveInterval);
    }

	 // ----------------------------------------------HttpSession Public Methods
	/**
     * Return the object bound with the specified name in this session, or
     * <code>null</code> if no object is bound with that name.
     *
     * @param name Name of the attribute to be returned
     *
     * @exception IllegalStateException if this method is called on an
     *  invalidated session
     */
    @Override
    public Object getAttribute(String name) {
    	Object value = super.getAttribute(name);
        
    	return value;

    }
    
    /**
     * Bind an object to this session, using the specified name.  If an object
     * of the same name is already bound to this session, the object is
     * replaced.
     * <p>
     * After this method executes, and if the object implements
     * <code>HttpSessionBindingListener</code>, the container calls
     * <code>valueBound()</code> on the object.
     *
     * @param name Name to which the object is bound, cannot be null
     * @param value Object to be bound, cannot be null
     *
     * @exception IllegalArgumentException if an attempt is made to add a
     *  non-serializable object in an environment marked distributable.
     * @exception IllegalStateException if this method is called on an
     *  invalidated session
     */
    @Override
    public void setAttribute(String name, Object value) {
        super.setAttribute(name, value);
        
        if(value==null)
        	return ;
        
        byte[] serializeValue = null;
		try {
			serializeValue = _manager.serializer.serializeFrom(this);
		} catch (IOException e) {
			e.printStackTrace();
		}
        
        _manager.getJedisTemplate().setex(this.id.getBytes(), this.maxInactiveInterval, serializeValue);
    }
    
    /**
     * Remove the object bound with the specified name from this session.  If
     * the session does not have an object bound with this name, this method
     * does nothing.
     * <p>
     * After this method executes, and if the object implements
     * <code>HttpSessionBindingListener</code>, the container calls
     * <code>valueUnbound()</code> on the object.
     *
     * @param name Name of the object to remove from this session.
     * @param notify Should we notify interested listeners that this
     *  attribute is being removed?
     */
    protected void removeAttributeInternal(String name, boolean notify) {
    	super.removeAttributeInternal(name, notify);
    	
    	//TODO reomve issue
    	
    	/*byte[] serializeValue = null;
		try {
			serializeValue = _manager.serializer.serializeFrom(this);
		} catch (IOException e) {
			e.printStackTrace();
		}
    	_manager.getJedisTemplate().setex(this.id.getBytes(), this.maxInactiveInterval, serializeValue);*/
    }
    
    /**
     * Perform the internal processing required to invalidate this session,
     * without triggering an exception if the session has already expired.
     *
     * @param notify Should we notify listeners about the demise of
     *  this session?
     */
    public void expire(boolean notify) {
    	super.expire(notify);
    }
    
}
