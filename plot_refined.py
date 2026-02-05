import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import os
import sys

# Percorso file
project_arg = sys.argv[1].lower() if len(sys.argv) > 1 else "bookkeeper"
# Se usi fold_results.csv (che ha i dati di tutti i fold per i baffi) usa questo:
CSV_PATH = '/Users/gaiameola/Desktop/Progetto/project-analyzer/csv_output/fold_results.csv'
project_name = project_arg.upper()

try:
    df = pd.read_csv(CSV_PATH)
    df.columns = df.columns.str.strip()

    # Pulizia nomi
    df['classifier_clean'] = df['Classifier'].str.split('_').str[0].str.replace('ibk', 'IBk', case=False).str.replace('naivebayes', 'NaiveBayes', case=False).str.replace('randomforest', 'RandomForest', case=False)

    # Selezioniamo le 5 metriche richieste
    metrics_to_show = ['Precision', 'Recall', 'AUC', 'Kappa', 'NPofB20']

    # Trasformiamo in formato "long" per avere la colonna 'Metric' da usare come colonna del grafico
    df_melted = df.melt(id_vars=['classifier_clean'], value_vars=metrics_to_show,
                        var_name='Metric', value_name='Value')
except Exception as e:
    print(f"Errore: {e}")
    sys.exit(1)

# --- CONFIGURAZIONE GRAFICA ---
sns.set_theme(style="whitegrid")

# Creazione del FacetGrid: una colonna per ogni metrica
g = sns.FacetGrid(df_melted, col="Metric", hue="classifier_clean",
                  sharey=False, height=5, aspect=0.7, palette="muted")

# Disegniamo i boxplot (i rettangoli con le linee)
g.map(sns.boxplot, "classifier_clean", "Value", order=['NaiveBayes', 'RandomForest', 'IBk'], showfliers=False)

# Aggiungiamo i punti sopra per far vedere la distribuzione (opzionale, rimuovi se vuoi solo i rettangoli)
g.map(sns.stripplot, "classifier_clean", "Value", order=['NaiveBayes', 'RandomForest', 'IBk'],
      alpha=0.3, jitter=0.2, color=".3")

# Pulizia titoli e assi
g.set_titles("{col_name}", size=12, fontweight='bold')
g.set_axis_labels("", "Score")
g.set_xticklabels(rotation=45)

# Titolo generale
plt.subplots_adjust(top=0.85)
g.fig.suptitle(f'Performance Distribution across Metrics - {project_name}', fontsize=16, fontweight='bold')

# Salvataggio
output_dir = f'plots/{project_arg}_detailed'
os.makedirs(output_dir, exist_ok=True)
plt.savefig(f'{output_dir}/faceted_boxplots.png', dpi=300, bbox_inches='tight')
plt.show()

print(f"✓ Grafico a rettangoli raggruppati generato: {output_dir}/faceted_boxplots.png")