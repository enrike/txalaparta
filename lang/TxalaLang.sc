/* TxalaLang. translation set for txalaparta supercollider GUIs
t = TxalaLang.new
t.lang = 2
t.do("sndpath is").postln
*/




TxalaLang{
	var strings, <>lang;

	*new { | lan=0 |
		^super.new.initTxalaLang(lan);
	}

	initTxalaLang { |lan|
		lang = lan;
		("LANG! is"+lan).postln;
		this.loadst();
	}

	do {arg origin;
		var trans;
		strings.do({arg item;
			if (item[0] == origin, {
				trans = item[lang];
			});
		});
		if (trans.isNil , {// just dont translate it
			trans=origin;
			["LANG! could not find translation for:", origin].postln;
		});
		if (trans == "" , {// just dont translate it
			trans=origin;
			["LANG! translation to be done:", origin].postln;
		});
		^trans;
	}

	loadst {
		strings = [
			["Digital Txalaparta", "Txalaparta Digital", "Txalaparta Digitala"],
			["About", "Info", "Honi buruz"],
			["Interactive Txalaparta", "Txalaparta Interactiva", "Txalaparta Interaktiboa"],
	["clear","borrar", "garbitu"],
	["sndpath is","sndpath es", "sndpath da"],
	["available samples are","los sonidos disponibles son", "eskuragarri dauden soinuak"],
	["auto loading general preferences ...","cargando preferencias generales...", "hobespen orokorrak kargatzen…"],
	["load sampleset","cargando sonidos", "soinuak kargatzen"],
	["Compass:","Compases", "Bueltak"],
	["\nBeats:","\nGolpes", "\nKolpeak"],
	["Beats:","Golpes", "Kolpeak"],
	["oops... too late to answer properly","demasiado tarde para responder a tiempo", "beranduegi garaiz erantzuteko"],
	["Interactive txalaparta by www.ixi-audio.net","Txalaparta Interactiva por www.ixi-audio.net", "www.ixi-audio.net -en txalaparta Interaktiboa"],
	["listen","escucha", "entzun"],
	["answer","responde", "erantzun"],
	["auto priority","auto prioridad", "autolehentasuna"],
	["priority","proridad", "lehentasuna"],
	["show score","linea de tiempo", "denbora-lerroa"],
	["scope","scope", "scope"],
	["meter","niveles", "mailak"],
	["Answer mode","Modo de respuesta", "Erantzuteko modua"],
	["imitation","imitacion", "kopiatu"],
	["percentage","porcentage", "ehunekoa"],
	["learning 1","aprende 1", "ikasten 1"],
	["learning 2","aprende 2", "ikasten 2"],
	["learning 4","aprende 4", "ikasten 4"],
	["changing to answer mode:","cambiando modo de respuesta:", "erantzuteko modua aldatzen"],
	["lick from memory","menoria", "memoriatik"],
	["volume","volumen", "bolumena"],
	["swing","variacion", "bariazioa"],
	["latency","latencia", "latentzia"],
	["HIT","GOLPE", "KOLPEA"],
	["PHRASE","FRASE", "ESALDIA"],
	["GAP","VACIO", "HUTSUNEA"],
	["Calibration manager","Control de calibracion", "Kalibrazio-kontrola"],
	["edit","editar", "editatu"],
	["could not set gain value","no he podido asignar el valor de gain", "ezin izan dut gain balioa esleitu"],
	["no predefined listen preset to be loaded","no hay un preset de escucha predeterminado", "ez dago entzuteko aurrez zehaztutako preset-ik"],
	["save","guardar", "gorde"],
	["Memory manager","Control de memorias", "Memorien kontrola"],
	["learn","aprende", "ikasi"],
	["reset","resetear", "egin reset"],
	["imitation mode has no memory to reset","modo de imitacion no tiene memoria que resetear", "imitazio-moduak ez du memoriarik reset egiteko"],
	["trying to load...","intentando cargar...", "kargatzeko saiakera…"],
	["imitation mode cannot load memory","modo de imitacion no puede cargar en memoria", "imitazio-moduak ezin du memorian kargatu"],
	["Chroma manager","Control de chroma", "Chromaren kontrola"],
	["chromagram data cleared","info de chroma borrada", "Chromaren informazioa ezabatu da"],
	["loading...","cargando...", "kargatzen"],
	["no predefined chroma preset to be loaded","no hay un preset de chroma predefinido", "ez dago aurrez zehaztutako chromaren presetik"],
	["Plank set manager","Control de tablas", "Taulen kontrola"],
	["sample new","grabar nuevo", "grabatu berria"],
	["no predefined plank preset to be loaded","no hay un preset de tablas predefinido", "ez dago aurrez zehaztutako taulen presetik"],
	["active planks are","las tablas activas son", "aktibatuta dauden oholak"],
	["curPattern is NIL!!","curPattern es NIL!!","curPattern NIL da!!"],
	["storing hit data","guardado informacion de los golpes","kolpeen informazioa gordetzen"],
	["reseting Markov Chain states","reseteando estados de cadenas de Markov","Markoven kateen egoeren reset egiten"],
	["cannot load memory file beause it does not match current answer system","",""],
	["Input calibration","Calibracion","Kalibrazioa"],
	["gain in","entrada","sarrera"],
	["Tempo detection","Deteccion de tempo","Tempoa hautematen"],
	["falltime","tiempo de caida","erortzeko denbora"],
	["rate","frecuencia","maiztasuna"],
	["Hutsune detection timeout","Tiempo limite de deteccion de hutsunes","Hutsuneak hautemateko gehieneko denbora"],
	["lookup","adelanto","aurrerapena"],
	["Hit onset detection","Deteccion de golpes","Kolpeak hautematea"],
	["threshold","limite","muga"],
	["relaxtime","tiempo de relax","relax-aldia"],
	["floor","suelo","lurra"],
	["mingap","hueco minimo","gutxieneko hutsunea"],
	["did not create a new sample set","no he creado un juego de samples nuevo","ez dut sanple-sorta berririk sortu"],
	["Locs >","Posiciones >","Kokapenak >"],
	["set name","nombre","izena"],
	["HELP","AYUDA","LAGUNTZA"],
	["Help","ayuda","laguntza"],
	["Each row represents a plank. Each button in the row is a position in the plank. Ideally left to right from the edge to the center. Select one of the positions by pressing the button and you have 10 secs to hit several times in the same plank location. On timeout the program processes the recording and tries to save each of the hits to a separated file. Repeat this for each of the positions in each of the planks. You dont have to fill all positions, one per plank is enough but the more the richer it will sound","",""],
	["processing","procesando","lanean"],
	["DONE PROCESSING","HECHO","EGINDA"],
	["0-0 detected","0-0 detectado","0-0 hauteman da"],
	// txalaset.sc
	["Plank set manager", "Gestor de tablas", "Oholen kontrola"],
	["Plank", "Tabla", "Ohola"],
		// autotxalaparta
	// txalatimecontrols
	["tempo swing","> swing","> swing"],
	["gap","apertura","tartea"],
	["gap swing","> swing","> swing"],
	["amp","vol","bolumena"],
	//
	["show score", "abre score", "ireki scorea"],
	["show animation", "abre animacion", "ireki animazioa"],
	["Hits", "Golpes", "Kolpeak"],
	["% chance", "% ", "% aukera"],
	["play", "play", "jo"],
	["Plank set", "Tablas", "Oholak"],
	["Presets", "Configuraciones", ""],
	["last emphasis", "enfasis ultimo", "indartu azkena"],
			// score
	["Planks", "Tablas", "Oholak"],

	["zoom", "zoom", "zooma"],
	["save MIDI", "guarda MIDI", "gorde MIDI"],
	["mode", "modo", "modua"],
	["draw group", "dibuja grupo", "margoztu taldea"],
	["Timeline", "Linea tiempo", "Debora marra"],
			// param win
			["Control", "Control", "Kontrola"],
			["Duration", "Duracion", "Iraupena"],
			["play/pause", "play/pause", "jo/gelditu"],
		]

	}
}



