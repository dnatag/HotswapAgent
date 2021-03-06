package org.hotswap.agent.plugin.weld;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.CDI;

import org.hotswap.agent.plugin.hotswapper.HotSwapper;
import org.hotswap.agent.plugin.weld.command.BeanDeploymentArchiveAgent;
import org.hotswap.agent.plugin.weld.testBeans.DependentHello;
import org.hotswap.agent.plugin.weld.testBeans.HelloProducer;
import org.hotswap.agent.plugin.weld.testBeans.HelloService;
import org.hotswap.agent.plugin.weld.testBeans.HelloServiceImpl;
import org.hotswap.agent.plugin.weld.testBeansHotswap.DependentHello2;
import org.hotswap.agent.plugin.weld.testBeansHotswap.HelloProducer2;
import org.hotswap.agent.plugin.weld.testBeansHotswap.HelloServiceImpl2;
import org.hotswap.agent.util.ReflectionHelper;
import org.hotswap.agent.util.test.WaitHelper;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Hotswap class files of weld beans.
 *
 * See maven setup for javaagent and autohotswap settings.
 *
 * @author Jiri Bubnik / modified by Vladimir Dvorak
 */
@RunWith(WeldJUnit4Runner.class)
public class WeldPluginTest {

    public <T> T getBean(Class<T> beanClass) {
        BeanManager beanManager = CDI.current().getBeanManager();
        Bean<T> bean = (Bean<T>) beanManager.resolve(beanManager.getBeans(beanClass));
        T result = beanManager.getContext(bean.getScope()).get(bean, beanManager.createCreationalContext(bean));
        return result;
    }

    /**
     * Check correct setup.
     */
    @Test
    public void basicTest() {
        assertEquals("Service:Hello", getBean(HelloService.class).hello());
        assertEquals("Dependent:Service:Hello", getBean(DependentHello.class).hello());
    }


    /**
     * Switch method implementation (using bean definition or interface).
     */
    @Test
    public void hotswapServiceTest() throws Exception {
        /*
        HelloServiceImpl bean = getBean(HelloServiceImpl.class);
		assertEquals("Service:Hello", bean.hello());
        swapClasses(HelloServiceImpl.class, HelloServiceImpl2.class.getName());
        String v = bean.hello();
        assertEquals("Service2:ChangedHello", bean.hello());
        // ensure that using interface is Ok as well
        assertEquals("Service2:ChangedHello", getBean(HelloService.class).hello());

        // return configuration
        swapClasses(HelloServiceImpl.class, HelloServiceImpl.class.getName());
        assertEquals("Service:Hello", bean.hello());
        */
    }


    /**
     * Add new method - invoke via reflection (not available at compilation time).
     */
    @Test
    public void hotswapSeviceAddMethodTest() throws Exception {
        swapClasses(HelloServiceImpl.class, HelloServiceImpl2.class.getName());

        String helloNewMethodIfaceVal = (String) ReflectionHelper.invoke(getBean(HelloService.class),
                HelloServiceImpl.class, "helloNewMethod", new Class[] {});
        assertEquals("Hello from helloNewMethod", helloNewMethodIfaceVal);

        String helloNewMethodImplVal = (String) ReflectionHelper.invoke(getBean(HelloServiceImpl.class),
                HelloServiceImpl.class, "helloNewMethod", new Class[] {});
        assertEquals("Hello from helloNewMethod", helloNewMethodImplVal);

        // return configuration
        swapClasses(HelloServiceImpl.class, HelloServiceImpl.class.getName());
        assertEquals("Service:Hello", getBean(HelloServiceImpl.class).hello());
    }

    @Test
    public void hotswapRepositoryTest() throws Exception {
        /*
        HelloServiceImpl bean = getBean(HelloServiceImpl.class);
		assertEquals("Service:Hello", bean.hello());
        swapClasses(HelloProducer.class, HelloProducer2.class.getName());
        assertEquals("Service:ChangedHello", bean.hello());

        // return configuration
        swapClasses(HelloProducer.class, HelloProducer.class.getName());
        assertEquals("Service:Hello", bean.hello());
        */
    }

    @Test
    public void hotswapRepositoryNewMethodTest() throws Exception {
        assertEquals("Service:Hello", getBean(HelloServiceImpl.class).hello());
        swapClasses(HelloProducer.class, HelloProducer2.class.getName());

        String helloNewMethodImplVal = (String) ReflectionHelper.invoke(getBean(HelloProducer.class),
                HelloProducer.class, "helloNewMethod", new Class[] {});
        assertEquals("Hello from helloNewMethod2", helloNewMethodImplVal);

        // return configuration
        swapClasses(HelloProducer.class, HelloProducer.class.getName());
        assertEquals("Service:Hello", getBean(HelloServiceImpl.class).hello());
    }

    @Test
    public void hotswapPrototypeTest() throws Exception {
        assertEquals("Dependent:Service:Hello", getBean(DependentHello.class).hello());

        // swap service this prototype is dependent to
        swapClasses(HelloServiceImpl.class, HelloServiceImpl2.class.getName());
        assertEquals("Dependent:Service2:ChangedHello", getBean(DependentHello.class).hello());

        // swap Inject field
        swapClasses(DependentHello.class, DependentHello2.class.getName());
        assertEquals("Dependant2:Hello", getBean(DependentHello.class).hello());

        // return configuration
        swapClasses(HelloServiceImpl.class, HelloServiceImpl.class.getName());
        swapClasses(DependentHello.class, DependentHello.class.getName());
        assertEquals("Dependent:Service:Hello", getBean(DependentHello.class).hello());
    }

    /**
     * Plugin is currently unable to reload prototype bean instance.
     */
    @Test
    public void hotswapPrototypeTestFailWhenHoldingInstance() throws Exception {
        /*
        DependentHello dependentBeanInstance = getBean(DependentHello.class);
        assertEquals("Dependent:Service:Hello", dependentBeanInstance.hello());

        // swap service this is dependent to
        try {
            swapClasses(HelloServiceImpl.class, HelloServiceImpl2.class.getName());
            assertEquals("Dependent:Service2:Hello", dependentBeanInstance.hello());
            throw new IllegalStateException("Reload dependant bean should not be correctly initialized.");
        } catch (NullPointerException e) {
            // BeanServiceImpl2 contains reference to different bean. Because existing reference
            // is not changed, this reference is null
        }

        // return configuration
        swapClasses(HelloServiceImpl.class, HelloServiceImpl.class.getName());
        assertEquals("Dependent:Service:Hello", getBean(DependentHello.class).hello());
        */
    }

    private void swapClasses(Class original, String swap) throws Exception {
        BeanDeploymentArchiveAgent.reloadFlag = true;
        HotSwapper.swapClasses(original, swap);
        assertTrue(WaitHelper.waitForCommand(new WaitHelper.Command() {
            @Override
            public boolean result() throws Exception {
                return !BeanDeploymentArchiveAgent.reloadFlag;
            }
        }));

        // TODO do not know why sleep is needed, maybe a separate thread in weld refresh?
        Thread.sleep(100);
    }
}
