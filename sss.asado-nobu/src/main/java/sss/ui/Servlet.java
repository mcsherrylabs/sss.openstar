package sss.ui;

import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.server.*;
import sss.ui.nobu.NobuUI;
import sss.ui.nobu.Main.ClientNode;
import sss.ui.reactor.ReactorActorSystem$;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.util.Properties;

/**
 * Created by alan on 6/10/16.
 */
@SuppressWarnings("serial")
@WebServlet(value = "/*", asyncSupported = true)
//@VaadinServletConfiguration(productionMode = false, ui = NobuUI.class)
public class Servlet extends VaadinServlet {

    private final UIProvider uiProvider;

    public Servlet(UIProvider uiProvider) {
        this.uiProvider = uiProvider;
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
}
