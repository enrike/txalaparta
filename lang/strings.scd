/*
b=[ ["house", "etxea", "casa"]],

b.do({arg item;
if (item[0]=="house", {
b[0][2].postln;
});
})

~txstrings.do({arg item;
if (item[0]=="sndpath is", {
[~txalalang, item[~txalalang]].postln;
});
})
*/

/*
~english = (house: "house");
~basque = (house: "etxea");
~spanish = (house: "casa");

~labels = ~english

then replace "house" with ~labels[\house], etc....
*/



TxalaLang{
	var strings, >lang;

	*new { |  |
		^super.new.initTxalaLang();
	}

	initTxalaMarkov { ||
		txalalang = 0;
	}

	do {arg origin;
		strings.do({arg item;
			if (item[0] == origin, {
				[lang, item[lang]].postln;
			});
		})
	}

	load {
		strings = [
	["sndpath is","sndpath es", "sndpath da"],
	["available samples are","los sonidos disponibles son", ""],
	["auto loading general preferences ...","cargando preferencias generales...", ""],
	["load sampleset","cargando sonidos", "soinuak kargatzen"],
	["\nCompass:""Compases", "Bueltak"],
	["Beats:","Golpes", "Kolpe"],
	["oops... too late to answer properly","demasiado tarde para responder a tiempo", "beranduegi garaiz erantzuteko"],
	["Interactive txalaparta by www.ixi-audio.net","Txalaparta Interactiva por www.ixi-audio.net", "Txalaparta Interaktiboa www.ixi-audio.net"],
	["listen","escucha", "entzun"],
	["answer","responde", "erantzun"],
	["auto priority","auto prioridad", "auto lehentasuna"],
	["priority","proridad", ""],
	["show score","linea de tiempo", "denbora linea"],
	["scope in","", ""],
	["Answer mode","Modo de respuesta", "Erantzun modua"],
	["imitation","imitacion", "kopiatu"],
	["percentage","porcentage", "ehuneko"],
	["learning 1","aprendiendo 1", "ikasten 1"],
	["learning 2","aprendiendo 2", "ikasten 2"],
	["learning 4","aprendiendo 3", "ikasten 3"],
	["changing to answer mode:","cambiando modo de respuesta:", ""],
	["lick from memory","", ""],
	["volume","volumen", "bolumena"],
	["swing","variacion", ""],
	["latency","latencia", ""],
	["HIT","GOLPE", "KOLPE"],
	["PHRASE","FRASE", "ESALDI"],
	["GAP","VACIO", "HUTSUN"],
	["Calibration manager","Control de calibracion", ""],
	["edit","editar", "editatu"],
	["could not set gain value","no he podido asignar el valor de gain", ""],
	["no predefined listen preset to be loaded","no hay un preset de escucha predeterminado", ""],
	["save","guardar", "gorde"],
	["Memory manager","Control de memorias", ""],
	["learn","aprende", "ikasi"],
	["reset","resetear", "reseteatu"],
	["imitation mode has no memory to reset","modo de imitacion no tiene memoria que resetear", ""],
	["trying to load...","intentando cargar...", ""],
	["imitation mode cannot load memory","modo de imitacion no puede cargar en memoria", ""],
	["Chroma manager","Control de chroma", ""],
	["chromagram data cleared","info de chroma borrada", ""],
	["loading...","cargando...", ""],
	["no predefined chroma preset to be loaded","no hay un preset de chroma predefinido", ""],
	["Plank set manager","Control de tablas", ""],
	["sample new","grabar nuevo", "berria grabatu"],
	["no predefined plank preset to be loaded","no hay un preset de tablas predefinido", ""],
	["active planks are","las tablas activas son", "aktibatutak dauden oholak dira"],
	["curPattern is NIL!!","curPattern es NIL!!","curPattern NIL da!!"],
	["storing hit data","guardado informacion de los golpes",""],
	["reseting Markov Chain states","reseteando stados de cadenas de Markov",""],
	["cannot load memory file beause it does not match current answer system","",""],
	["Input calibration","Calibracion",""],
	["gain in","",""],
	["Tempo detection","Deteccion de tempo",""],
	["falltime","tiempo de caida",""],
	["rate","frecuencia",""],
	["Hutsune detection timeout","Tiempo limite de deteccion de hutsunes",""],
	["lookup","adelanto",""],
	["Hit onset detection","Deteccion de golpes",""],
	["threshold","limite",""],
	["relaxtime","tiempo de relax",""],
	["floor","suelo",""],
	["mingap","hueco minimo",""],
	["did not create a new sample set","no he creado un juego de samples nuevo",""],
	["Locs -->","Posiciones -->",""],
	["set name","nombre","izena"],
	["HELP","AYUDA","LAGUNTZA"],
	["Help","ayuda","laguntza"],
	["Each row represents a plank. Each button in the row is a position in the plank. Ideally left to right from the edge to the center. Select one of the positions by pressing the button and you have 10 secs to hit several times in the same plank location. On timeout the program processes the recording and tries to save each of the hits to a separated file. Repeat this for each of the positions in each of the planks. You dont have to fill all positions, one per plank is enough but the more the richer it will sound","",""],
	["processing","procesando","lanean"],
	["DONE PROCESSING","HECHO","EGINDA"],
	["0-0 detected","0-0 detectado","0-0 antzemanda"],
]

	}
}




//~txalalangs = ['eu', 'eu', 'es'];

