/*
 * This file is made available under the terms of the LGPL licence.
 * This licence can be retreived from http://www.gnu.org/copyleft/lesser.html.
 * The source remains the property of the YAWL Foundation.  The YAWL Foundation is a
 * collaboration of individuals and organisations who are commited to improving
 * workflow technology.
 */

package au.edu.qut.yawl.worklet.support;


/**
 *  RdrConditionException Class.
 *
 *  An RdrConditionException is thrown when an attenpt is made to evaluate
 *  a rule's condition and is found to be malformed or does not evaluate
 *  to a boolean result.
 *
 *  @author Michael Adams
 *  BPM Group, QUT Australia
 *  m3.adams@qut.edu.au
 *  v0.7, 10/12/2005
 */

public class RdrConditionException extends Exception {
   RdrConditionException() {
       super();
   }
   
   RdrConditionException(String message) {
       super(message);
   }

}  