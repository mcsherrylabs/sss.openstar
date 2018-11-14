package sss.ui;

import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.server.*;
import sss.ui.nobu.NobuUI;
import sss.ui.nobu.Main.ClientNode;
import sss.ui.reactor.ReactorActorSystem$;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.util.Properties;
import java.util.function.UnaryOperator;

/**
 * Created by alan on 6/10/16.
 */
@SuppressWarnings("serial")
@WebServlet(value = "/*", asyncSupported = true)
//@VaadinServletConfiguration(productionMode = false, ui = NobuUI.class)
public class Servlet extends VaadinServlet implements SessionDestroyListener {

    public final static String SessionAttr = "NOBUATTR";

    private final UIProvider uiProvider;
    private final UnaryOperator<String> broadcastSessionEnd;

    public Servlet(UIProvider uiProvider, UnaryOperator<String> broadcastSessionEnd) {
        this.uiProvider = uiProvider;
        this.broadcastSessionEnd = broadcastSessionEnd;
    }

    @Override
    protected void servletInitialized() throws ServletException {
        getService().addSessionInitListener(new SessionInitListener() {
            @Override
            public void sessionInit(SessionInitEvent event) throws ServiceException {
                event.getSession().addUIProvider(uiProvider);
            }
        });
    }


    @Override
    public void destroy() {
        ReactorActorSystem$.MODULE$.blockingTerminate();
        log("Terminated actor system!");
        super.destroy();
    }

    @Override
    public void sessionDestroy(SessionDestroyEvent event) {
        if(event.getSession() != null) {
            Object attr = event.getSession().getAttribute(SessionAttr);
            if(attr != null) {
                broadcastSessionEnd.apply(attr.toString());
            }
        }

    }
}
