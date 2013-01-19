package si.ddejan.mobitel.si;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.StringTokenizer;

import com.gargoylesoftware.htmlunit.Page;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomNode;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTable;
import com.gargoylesoftware.htmlunit.html.HtmlTableRow;

/** Namen tega class ja preprost nadzor nad stanjem na mobi računu in polnenje mobi računa. </p>
 * 
 * Potrebuje <a href="http://htmlunit.sourceforge.net">"htmlunit"<a/> v classpath.
 * 
 * @author Dejan D */
public class MobiRacun{
	private String telephone_number= null;
	private String puk_code= null;

	private static final String LOGIN_FORM= "https://monitor.mobitel.si/mobinew/ppPukRefillLogin.jsp";
	private static final String LOGIN_FAIL= "Telefonska številka in PUK nista pravilni";

	/** <code>SimpleDateFormat("yyyy-MM-dd")</code> */
	public static final SimpleDateFormat formatDate= new SimpleDateFormat(
			"yyyy-MM-dd" );
	/** <code>DecimalFormat("#.##")</code> */
	public static final DecimalFormat formatFloat= new DecimalFormat( "#.##" );

	protected WebClient browser= null;
	protected Page loged_in= null;

	private boolean autoRefresh= false;

	// STATUS RAČUNA
	private Float racunStanje= null;
	private Date racunVeljaDo= null;
	private Boolean racunAktiven= null;

	/** Klikne gumb "preberi stanje"
	 * 
	 * @return TRUE če je uspel klikniti */
	private boolean clickPreberiStanje() {
		Iterable<HtmlAnchor> links= ((HtmlPage) loged_in).getAnchors();

		HtmlAnchor submit_button= null;
		for (HtmlAnchor link : links)
			if (link.toString().indexOf( "StatusForm.submit()" ) >= 0) {
				submit_button= link;
				break;
			}
		try {
			loged_in= submit_button.click();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/** Ko so podatki o stanju že naloženi oz. je bil kliknjen gumb naloži stanje sparsa vrednosti
	 * 
	 * @see #clickPreberiStanje()
	 * @return če je šlo brez problemov */
	private boolean preberiStanjeIzForme() {
		try {
			if (isSeasionValid( true ) && clickPreberiStanje()) {

				@SuppressWarnings("unchecked")
				List<HtmlTable> tables= (List<HtmlTable>) ((HtmlPage) loged_in)
						.getByXPath( "/html/body/form[1]/table" );

				HtmlTable table= null;
				for (HtmlTable table_temp : tables)
					if (table_temp.asText().indexOf( "Stanje" ) >= 0) {
						table= table_temp;
						break;
					}

				for (final HtmlTableRow row : table.getRows()) {
					if (row.getCell( 0 ).asText().indexOf( "Stanje:" ) >= 0) {
						try {
							StringTokenizer st= new StringTokenizer( row
									.getCell( 1 ).asText().replace( ',', '.' ),
									" " );
							racunStanje= Float.parseFloat( st.nextToken() );
						} catch (Exception e) {
						}
						continue;
					}

					if (row.getCell( 0 ).asText().indexOf( "Velja DO:" ) >= 0) {
						try {
							SimpleDateFormat df= new SimpleDateFormat(
									"dd.MM.yyyy" );
							racunVeljaDo= df.parse( row.getCell( 1 ).asText() );
						} catch (Exception e) {
							e.printStackTrace();
						}
						continue;
					}
					if (row.getCell( 0 ).asText().indexOf( "Status:" ) >= 0) {
						try {
							racunAktiven= Boolean.valueOf( row.getCell( 1 )
									.asText().equals( "Aktiven" ) );
						} catch (Exception e) {
							e.printStackTrace();
						}
						continue;
					}

				}

			}

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
		return true;
	}

	/** Stanja ne računu v EUR
	 * 
	 * @return Stanje na računu v EUR */
	public Float getStanje() {
		if (isAutoRefresh() || (racunStanje == null))
			preberiStanjeIzForme();
		return racunStanje;

	}

	/** Datum do katerega je veljaven račun
	 * 
	 * @return vrne Datum kdaj poteče mobi račun */
	public Date getVeljavnost() {
		if (isAutoRefresh() || (racunVeljaDo == null))
			preberiStanjeIzForme();
		return racunVeljaDo;
	}

	/** Aktivnost računa
	 * 
	 * @return true če je račun aktiven, false če je račun neaktiven. */
	public Boolean jeAktiven() {
		if (isAutoRefresh() || (racunAktiven == null))
			preberiStanjeIzForme();
		return racunAktiven;
	}

	/** Preveri če smo prijavljeni v spletno stran
	 * 
	 * @param reload_page ali naj pred preverjanjem še enkrat naloži spletno stran
	 * @return */
	private boolean isSeasionValid(boolean reload_page) {
		if (reload_page == true)
			try {
				loged_in= ((HtmlPage) loged_in).refresh();
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}

		if (((DomNode) loged_in).asText().toString().indexOf( LOGIN_FAIL ) == -1)
			return true;
		else
			return false;

	}

	/** Mapolni mobi račun z kodo
	 * 
	 * @param koda (16 mestna) za polnitev računa */
	public boolean napolni(String koda) {
		HtmlForm form= ((HtmlPage) this.loged_in).getFormByName( "RefillForm" );
		form.getInputByName( "scratch" ).setValueAttribute( koda );

		Iterable<HtmlAnchor> links= ((HtmlPage) this.loged_in).getAnchors(); // page.getHtmlElementDescendants();

		HtmlAnchor submit_button= null;
		for (HtmlAnchor link : links)
			if (link.toString().indexOf( "RefillForm.submit()" ) >= 0) {
				submit_button= link;
				break;
			}

		try {
			HtmlPage result= submit_button.click();
			if (result.asText().toString()
					.indexOf( "Vpisana Mobikartica ni pravilna" ) >= 0)
				return false;

		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}

		// Račun je bil napolnjen.. preberi stanje..
		preberiStanjeIzForme();
		return true;
	}

	/** Prijavi uporavnika v stran
	 * 
	 * @return true če je prijava uspešna, sicer false */
	public boolean login() {
		try {
			HtmlPage page= browser.getPage( LOGIN_FORM );

			HtmlForm form= page.getFormByName( "RefillForm" );

			form.getInputByName( "msisdn" ).setValueAttribute(
					this.getTelephone_number() );
			form.getInputByName( "puk" ).setValueAttribute( this.getPuk_code() );

			// Find a "submit" button
			Iterable<HtmlAnchor> links= page.getAnchors(); // page.getHtmlElementDescendants();

			HtmlAnchor submit_button= null;
			for (HtmlAnchor link : links)
				if (link.toString().indexOf( "RefillForm.submit()" ) >= 0) {
					submit_button= link;
					break;
				}
			if (submit_button == null)
				throw new Exception( "Could not find a SUBMIT button" );
			loged_in= submit_button.click();

			return isSeasionValid( false );

		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}

	}

	{
		browser= new WebClient();
		browser.getOptions().setThrowExceptionOnFailingStatusCode( false );
	}

	/** @see #setPuk_code(String)
	 * @see #setTelephone_number(String)
	 * @see #login() */
	public MobiRacun() {
		super();
	}

	/** @see #login()
	 * @param telephone_number
	 * @param puk_code */
	public MobiRacun(String telephone_number, String puk_code) {
		super();
		setTelephone_number( telephone_number );
		setPuk_code( puk_code );
	}

	public String getTelephone_number() {
		return telephone_number;
	}

	public void setTelephone_number(String telephone_number) {
		this.telephone_number= telephone_number;
	}

	public String getPuk_code() {
		return puk_code;
	}

	public void setPuk_code(String puk_code) {
		this.puk_code= puk_code;
	}

	/** Preveri če bodo podatki osveženi pred vsakim branjem podatkov get
	 * @return boolean če je vklopljeno avtomatko osveževenje */
	public boolean isAutoRefresh() {
		return autoRefresh;
	}

	/** Nastavitev avtomatskega osveževanja
	 * @param autoRefresh */
	public void setAutoRefresh(boolean autoRefresh) {
		this.autoRefresh= autoRefresh;

	}

	@Override public String toString() {
		//@formatter:off
		return "MobiRacun [telephone_number=" + telephone_number
						+ ", stanjeNaRacunu=" + racunStanje 
						+ ", veljaDoRacun="	+ formatDate.format(racunVeljaDo)  
						+ ", statusRacuna="+ racunAktiven + "]";
		//@formatter:on
	}

	public static void main(String[] args) {
		final String napolniCmd= "-napolni";
		//@formatter:off
		final String param = 
				"MobiRacun:" + "\n"+
				"parametri: telefonska_številka geslo_puk_koda "+ 
									"["+ napolniCmd + " koda_za_polnit]";
		//@formatter:on
		// Namen
		try {
			MobiRacun mr= null;
			if (args.length == 2)
				mr= new MobiRacun( args[0], args[1] );
			else
				throw new Exception( "Neveljavni parametri" );

			if (mr.login()) {
				System.out.println( "Uspešna prijava v račun" );
				if ((args.length == 4) && (args[2].equals( napolniCmd ))) { // POLNITEV RAČUNA
					String koda= args[3];
					System.out.print( "Polnjenje Mobi računa: " );
					if (mr.napolni( koda ))
						System.out.println( "USPEŠNO" );
					else
						System.out.println( "NEUSPEŠNO" );
				} else { // NI POLNITEV -- IZPIŠI STANJE
					//@formatter:off
					System.out.println( 
							"Stanje_na_racunu: "+ formatFloat.format( mr.getStanje() ) + "\n"+
							"Veljavno_do: "+ formatDate.format( mr.getVeljavnost() )+ "\n"+
							"Aktivnost_racuna: "+ mr.jeAktiven() 	);
					//@formatter:on
				}

			} else
				throw new Exception( "login failed" );
		} catch (Exception e) {
			System.out.println( "Napaka:" );
			System.out.println( e.toString() );
			System.out.println( "*****************************************" );
			System.out.println( param );
		}

	}

}
