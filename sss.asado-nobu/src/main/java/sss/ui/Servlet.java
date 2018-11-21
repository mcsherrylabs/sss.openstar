package sss.ui;

import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.server.*;
import sss.ui.nobu.NobuUI;
import sss.ui.nobu.Main.ClientNode;


import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import java.util.Properties;
import java.util.function.UnaryOperator;

/**
 * Created by alan on 6/10/16.
 */
@SuppressWarnings("serial")
@WebServlet(value = "/*", asyncSupported = true)
//@VaadinServletConfiguration(productionMode = true, ui = NobuUI.class)
public class Servlet extends VaadinServlet {

    public final static String SessionAttr = "NOBUATTR";

    private final UIProvider uiProvider;
    private final UnaryOperator<String> broadcastSessionEnd;

    public Servlet(UIProvider uiProvider, UnaryOperator<String> broadcastSessionEnd) {
        this.uiProvider = uiProvider;
        this.broadcastSessionEnd = broadcastSessionEnd;
    }


    @Override
    protected void servletInitialized() throws ServletException {
        getService().addSessionDestroyListener( event -> {
            if (event.getSession() != null) {
                String attr = event.getSession().getSession().getId();
                if (attr != null) {
                    broadcastSessionEnd.apply(attr);
                }
            }

        });

        getService().addSessionInitListener( event ->
                event.getSession().addUIProvider(uiProvider)
        );
    }


    @Override
    public void destroy() {
        super.destroy();
    }

}
