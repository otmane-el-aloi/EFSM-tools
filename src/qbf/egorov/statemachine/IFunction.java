/**
 * IFunction.java, 05.03.2008
 */
package qbf.egorov.statemachine;

/**
 * TODO: add comment
 *
 * @author Kirill Egorov
 */
public interface IFunction extends IAction {

    public Object getCurValue();

    public Class getReturnType();
}
