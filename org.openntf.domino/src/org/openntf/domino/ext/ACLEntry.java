/*
 * Copyright OpenNTF 2013
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at:
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or 
 * implied. See the License for the specific language governing 
 * permissions and limitations under the License.
 */
package org.openntf.domino.ext;

import org.openntf.domino.ACL;

// TODO: Auto-generated Javadoc
/**
 * The Interface ACLEntry.
 * 
 * @author withersp
 */
public interface ACLEntry {

	/**
	 * Set ACLEntry Level to an ACL.Level. Options are:
	 * <ol>
	 * <li>NOACCESS(ACL.LEVEL_NOACCESS)</li>
	 * <li>DESPOSITOR(ACL.LEVEL_DEPOSITOR)</li>
	 * <li>READER(ACL.LEVEL_READER)</li>
	 * <li>AUTHOR(ACL.LEVEL_AUTHOR)</li>
	 * <li>EDITOR(ACL.LEVEL_EDITOR)</li>
	 * <li>DESIGNER(ACL.LEVEL_DESIGNER)</li>
	 * <li>MANAGER(ACL.LEVEL_MANAGER)</li>
	 * </ol>
	 * 
	 * @param level
	 *            ACL.Level to set to
	 */
	public void setLevel(ACL.Level level);

}
