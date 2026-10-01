/* ============================================================
   Chat Jr — Aplicação principal
   Contém: modelo de dados, roteador de views, integração com
   API Anthropic (Claude), leitura local de PDF (pdf.js) e
   persistência em localStorage.
   ============================================================ */

/* ============================================================
   0. UTILIDADES
   ============================================================ */

/**
 * Logo da Empresa JR usado dentro do app (avatar do assistente,
 * topbar e sidebar). Mantido em base64 para o site funcionar sem
 * depender de nenhum recurso externo.
 */
const LOGO_SRC = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAJAAAACQBAMAAAAVaP+LAAAAGFBMVEX///6AkfIJKOgHKOkHKOcHKOUGKOYDIuemGAv3AAAHy0lEQVR42u2a22/b1h3Hv+dQKdolNg8l2y0QR6LouAXaxVGcDgO2+ZK0e3LqxG2ylwHBEmT5c/a+S1202PZQp47ntA/rkMRe6z4MjaM42YIhtUWpDlJYFnnkCyxsIs8edJdIipT6MiB6kkDxw9/vd36X7zkSeRvfz4viOeg56P8LxAgjgotuQLRXiJ1oykgZ4ThFmnUMsnWhW6GTmsZZPrcR9wQRr6IV8TysXWbCTgsk9nNepJDXU5RkLB3bGQWQp8busRw6BeUN6S3IcwBCMzDvjuSDu8YZYOvHT80JDhOAAqJcvG1QldouIElzgJuvv/BYfZa92Pf+wXeFAgAUCgf8X6H3Dn038Ap95aHs0yI7ksaxVFy7YfLG7yq/vhGxjVBR82vRf/ZJLHdi9lmh6cLB43deoz+wBoVfkDFkfHP6z7z1ysHG5ptfHXnGfK6a0IqhqVnuFFKTYzSp+A0214s/+YC7rI5hjb3MJbnQFiTY45deO+HKAYwB+9/EfKltrZEIjWuz3KNu/jEkopS3L1oh7BvcK9/N9y8kWwu4NUZ5sbdZ8Gw9Bxs/f+zDNU7/y2sp6FjwWysxtbnTtbpG2GT1/avXrziGKTuUiUrcGyRkq/osMj1HnFfuo3Um83YJqdbeWqpLmLZ+uQy1jWs8XeemG0j8SZMCTRHd7cJW1AwEcu3S4o8n4hnVN8hjmG0N3Rlb9tuzIaXVpnwSHDABQHzUi5PcL6jyCl+r//Tkrglg67Kelj1BsdbcCd2QTz3soQImkTOJ/avzKUAsRieSQS1COH6fbwOSBQMb9NYvfsuB7DkdgUF89XR1ibbzG3euznKYC6NiR3YHsTqxUK0Wohz9XbVhTYu1FYVD7Bmaoei+LKKV9GU8VU1A84Nr5rdnUkB29I6qBxRaBrlfSy7jD6cS/1QBgcRad4rNlNL2DCAWv5BZQFBjiosPBV8GkJWFCAgiaOg+W5M9wwwwx1Ti3tgUJ4kw2lS+88fBADx92KWqFXv3dABi6YTeHQjA4RkA2VS3Ojt7IQIAYrJrwS5KPtUmbqcgyQIAccuvRZbrhXipmEnXwS7daI91Cyq3iJqGD3XG6ceq0Vg6HVrE6HHRuGzU/yirL71ptVR8YjHGuIusSdVvFCQX0BK+qVQzIV24NhxNTpQM4ZU4dRRsOvWFXamypGSzjkHDm9bwX8vbDSY03qlr4akijpZXKzfBhI8Y2cfKb+7XbwevpdPD89WPQ4oPUG14J2qcgU8y2tHakE0GS8jiTBkTvn6OIjNbmwoiWIkoX2kwoaB/ck7qPftx/ShIBlo1MRIuRW1ei/V9nKq7ktCD5VFKX1fz1KDja3IDhz6I5n0EuxpTIqshkCgbD9ObqQZTc4ofi6q1RmOHZoAN7e8jP/2N48P8umbtf86BgakWvZyXgq2aOJwCwHFkLcUapvdOwMb2dB4AxJMJ+1Bjw8qVatY3qOyS/ZmUnWkUhZCDjKOTFTXC5USj6KVKOohFVR28NfHtSoNvk+UdVNA2Im5Fi1ecerzPVas9j2eGGn3TWUcWITtWfMoaZrdb83c8Squ1I0GSvO4bROIuIGEITzEqPpV+2JAAEu9wHG1PkmXW+gzfoOq9Ru+t4RoovEw6tUgsFFdrvkmCJzqdtHsJRgIeHzqNEyAboSvVDsASVqlnd2CRYHqxcqhF3kllWKeuiQ85qkGyuxFa2QQqyU26UmwiKifLoEimK9CnuyMzlWHkfhBlJtvu14yeiqo9IgKr2vqOv50IldoiOZ9Pd6Vq/7Lw9RUAIKEHXelssTceFQAQuR13l8eqnwQ4xj9jAMKBS6TZpBUBxkGmHrlvjkW9zLapQ60BEPr24LsAScceeORRwo9v77EVhv6k76MxN6iww5yB9eX9gojrEdu9N95Vpo9aSsAdJG8Rzthduf6JlIq6lwh3GtUthwrbZ3JvrCs5ycO1PKtzTKoawJoLd2eTK7Q37c81qXIStdkS9e2wDYD4PK1ZqNyfTTZHyRxzUwe1o7FqtvaLyu2XLrSsW9Npfciz98T00oet3wvuW69UMHVFy9j+/bIgMNtlmMMxtFT/XVdD+u8ybxCRa8cY5zM/c035wXhbi2rbBstyDU2YNZ11t4JqB3n9GSa7GfQrOa22AdXFOtm35HJpYPHpCG8DSu6WF4RMsQcpl7W+LIr5NsHGaHXTuJtziTUZXp/UUu2CDbusyPutU73OsR44t6mbrB0oyQYVAKDTjx7ddF6xywux9plNCKUXGUCG52MjjgaRfv3CPbV9iYwgvXiVkYHxs9agE4i8Or6aCbdsAFt/FuMvHvz4a9F36XNr/cuCE6fnhdcLcd4eBLy4s/mjl++OkzeXnDjnMmkUePsYAUCRZO8pCDmEmgxNLznPKMeRraZDishvtj5WGZjeCDOD+OlHALD01kaC0ljzz30kPHD+Tob1ZhzXwOnXdXVZRc+eHTl8s776lfDF7Jfj+fW9k6ZfUCXFDyX+xk3ABGEg7JI1r6m6cBm+3n9l4KFJYA7AcALW/KhX3/bUR5LGniSJEpoU89uQzqzJXirB8x9RGyQyQsV6xNC4hfyO3KlFeFvc5qv0LHrWI8hrKXRqkaxLCfv+6XVOezm1aTTfsWuMlzbCghCbimADskmls/KEE0SgC1CQY9fnoOeg7+H1P6Lk2O0KkQSBAAAAAElFTkSuQmCC';

// aplica o logo nos <img> do topo da landing e do sidebar
document.addEventListener('DOMContentLoaded', () => {
  ['jr-logo-top', 'jr-logo-side'].forEach(id => {
    const el = document.getElementById(id);
    if (el) el.src = LOGO_SRC;
  });
});

/**
 * Escapa uma string para inserção segura em HTML.
 * @param {any} s texto a escapar
 * @returns {string} texto com caracteres especiais convertidos em entidades HTML
 */
function esc(s){ const d=document.createElement('div'); d.textContent = s==null?'':String(s); return d.innerHTML; }

/**
 * Remove tags HTML de uma string e normaliza espaços.
 * @param {string} h HTML de entrada
 * @returns {string} texto puro
 */
function stripHtml(h){ const d=document.createElement('div'); d.innerHTML=h||''; return (d.textContent||'').replace(/\s+/g,' ').trim(); }

/**
 * Converte um nome legível em slug para uso como id de aba.
 * Remove acentos, converte para minúsculo e troca não-alfanuméricos por hífen.
 * @param {string} s texto a transformar
 * @returns {string} slug (ex: "resumo-executivo")
 */
function slugify(s){
  return String(s).toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'')
    .replace(/[^a-z0-9]+/g,'-').replace(/(^-+|-+$)/g,'') || 'aba';
}

/** @returns {string} data de hoje formatada em pt-BR (ex: "23 set 2026") */
function today(){
  return new Date().toLocaleDateString('pt-BR',{day:'2-digit',month:'short',year:'numeric'});
}

/**
 * Extrai as iniciais de um nome (até duas letras).
 * @param {string} name nome completo
 * @returns {string} iniciais em maiúsculo (ex: "AS")
 */
function initialsOf(name){
  return String(name||'?').trim().split(/\s+/).slice(0,2).map(w=>w[0]).join('').toUpperCase();
}

/**
 * Converte texto puro em HTML, preservando parágrafos e quebras de linha.
 * @param {string} t texto puro
 * @returns {string} HTML com <p> e <br>
 */
function textToHtml(t){
  return t.split(/\n{2,}/).map(p=>'<p>'+esc(p).replace(/\n/g,'<br>')+'</p>').join('');
}

let modalReturnFocus=null;
function openModal(id){
  const m=document.getElementById(id);
  modalReturnFocus=document.activeElement;
  m.classList.remove('hidden');
  const f=m.querySelector('input:not([type=hidden]),select,textarea,button');
  if(f) f.focus();
}
function closeModal(id){
  document.getElementById(id).classList.add('hidden');
  if(modalReturnFocus && document.contains(modalReturnFocus)) modalReturnFocus.focus();
}
// Esc fecha o diálogo aberto
document.addEventListener('keydown',e=>{
  if(e.key!=='Escape') return;
  const open=[...document.querySelectorAll('.modal-overlay:not(.hidden)')].pop();
  if(open) closeModal(open.id);
});
function scrollToLogin(){
  const c=document.getElementById('login-card');
  c.scrollIntoView({behavior:'smooth',block:'center'});
  setTimeout(()=>document.getElementById('login-email').focus(),400);
}

/* ============================================================
   1. TEMA
   ============================================================ */
let currentTheme='light';
function applyTheme(theme){
  currentTheme=theme;
  document.documentElement.setAttribute('data-theme',theme);
  document.getElementById('theme-icon-sun').classList.toggle('hidden',theme==='dark');
  document.getElementById('theme-icon-moon').classList.toggle('hidden',theme!=='dark');
  const s=document.getElementById('theme-settings-switch');
  if(s) s.classList.toggle('on',theme==='dark');
}
function toggleTheme(){ applyTheme(currentTheme==='dark'?'light':'dark'); }
applyTheme('light');

/* ============================================================
   2. MODELO DE DADOS
   ============================================================ */

/** Estrutura padrão de um anexo (PDF + link) associado a uma aba. */
function emptyAttachment(){
  return {pdfName:'', pdfText:'', pdfData:null, slideLink:'', processed:false, extracted:0, sections:[], knowledge:'', error:'', processedAt:''};
}

/**
 * Cria uma aba do plano com nome, HTML inicial e anexo opcional.
 * @param {string} name Nome exibido da aba
 * @param {string} html Conteúdo HTML inicial
 * @param {object} attach Dados de anexo (opcional)
 */
function makeTab(name, html, attach){
  return {id:slugify(name), name, title:name, html:html||'', published:true, meta:emptyMeta(),
          attachment:Object.assign(emptyAttachment(), attach||{})};
}

/** Metadados estruturados de uma etapa do plano. */
function emptyMeta(){
  return {shortDescription:'', whatIsIt:'', objective:'', keyPoints:[], suggestedQuestions:[], source:'', updatedAt:''};
}
/** Definição genérica de cada etapa padrão (vale para qualquer empresa; não traz fatos do cliente). */
const STAGE_INFO = {
  'Resumo Executivo':['Visão resumida do negócio e das principais conclusões do plano.','Permitir que qualquer leitor entenda o plano inteiro em poucos minutos.'],
  'Empresa':['Apresentação da empresa: origem, sócios e proposta.','Contextualizar quem é a empresa e de onde ela parte.'],
  'Produto/Serviço':['Descrição do que a empresa vende e de como entrega.','Deixar claro o que é ofertado ao cliente.'],
  'Proposta de Valor':['Promessa central que diferencia a empresa para o cliente.','Explicar por que o cliente escolheria esta empresa.'],
  'Mercado':['Análise do mercado em que a empresa atua.','Mostrar o tamanho e o comportamento do mercado.'],
  'Público-Alvo':['Descrição de quem são os clientes que a empresa quer atender.','Orientar comunicação, produto e preço a partir de quem se quer atingir.'],
  'Concorrentes':['Mapa dos concorrentes e de como eles se posicionam.','Comparar a empresa com as alternativas do cliente.'],
  'Análise SWOT':['Forças, fraquezas, oportunidades e ameaças do negócio.','Dar uma visão equilibrada dos pontos internos e externos.'],
  'Plano Financeiro':['Projeções de receita, custos e ponto de equilíbrio.','Mostrar se e quando o negócio se sustenta financeiramente.'],
  'Riscos':['Riscos identificados para o negócio.','Antecipar o que pode dar errado para poder se preparar.'],
  'Metas':['Metas definidas para o negócio.','Dar direção e critérios de acompanhamento.'],
  'Estratégia de Marketing':['Plano de comunicação e de atração de clientes.','Definir como a empresa chega até o público-alvo.'],
  'Investimento Inicial':['Valor e destino do investimento para abrir o negócio.','Mostrar quanto é preciso investir e em quê.'],
  'VPL, TIR e Payback':['Indicadores de retorno do investimento.','Avaliar se o investimento compensa e em quanto tempo volta.'],
  'Análise de Sensibilidade':['Como os resultados mudam em cenários diferentes.','Mostrar a resistência do plano a variações.']
};
/** Preenche metadados de uma etapa já escrita com a definição genérica da etapa. Pontos principais só vêm da IA ou da edição manual. */
function deriveMeta(tab){
  const m=emptyMeta();
  const info=STAGE_INFO[tab.name];
  if(info){ m.whatIsIt=info[0]; m.objective=info[1]; }
  m.source=(tab.attachment&&(tab.attachment.pdfName||tab.attachment.slideLink))||'';
  m.updatedAt=(tab.attachment&&tab.attachment.processedAt)||'';
  return m;
}
/** Remove tudo que não esteja na lista de tags permitidas (HTML vindo da IA). */
function sanitizeHtml(html){
  const ALLOWED=new Set(['P','B','STRONG','UL','OL','LI','H4','TABLE','THEAD','TBODY','TR','TH','TD','BR']);
  const doc=new DOMParser().parseFromString('<body>'+String(html||'')+'</body>','text/html');
  (function walk(node){
    [...node.childNodes].forEach(ch=>{
      if(ch.nodeType===8){ ch.remove(); return; }
      if(ch.nodeType!==1) return;
      if(['SCRIPT','STYLE','IFRAME','OBJECT','EMBED','LINK','META'].includes(ch.tagName)){ ch.remove(); return; }
      walk(ch);
      if(ALLOWED.has(ch.tagName)){ [...ch.attributes].forEach(a=>ch.removeAttribute(a.name)); }
      else { while(ch.firstChild) ch.parentNode.insertBefore(ch.firstChild,ch); ch.remove(); }
    });
  })(doc.body);
  return doc.body.innerHTML;
}

const DEFAULT_TAB_NAMES = [
  'Resumo Executivo','Empresa','Produto/Serviço','Proposta de Valor','Mercado','Público-Alvo',
  'Concorrentes','Análise SWOT','Plano Financeiro','Riscos','Metas','Estratégia de Marketing'
];
function defaultTabs(){
  return DEFAULT_TAB_NAMES.map(n=>makeTab(n, emptyTabHtml()));
}
function emptyTabHtml(){
  return '<p style="color:var(--muted);">Esta aba ainda não tem conteúdo. Anexe o PDF ou o link desta parte do plano no painel acima e clique em "Processar com IA" — o texto será escrito a partir do material enviado. Você também pode escrever o conteúdo manualmente pelo botão de editar da aba.</p>';
}

/**
 * Monta o conjunto de abas do cliente de demonstração (Norte Fit Studios).
 * Já vem com conteúdo processado, para o app iniciar populado.
 */
function demoTabs(){
  const t = [
    ['Resumo Executivo','<p>A Norte Fit Studios é um estúdio de treino funcional em grupo, com sede em unidade própria de 220m², voltado a adultos de 25 a 45 anos que buscam resultado físico real com acompanhamento próximo.</p><p>O modelo de negócio é baseado em mensalidades recorrentes, com meta de atingir 300 alunos ativos em 12 meses e abrir uma segunda unidade até o mês 10.</p>'],
    ['Empresa','<p>Fundada em 2025, a Norte Fit Studios nasceu da experiência dos dois sócios como personal trainers, unindo metodologia de treino funcional a uma proposta de comunidade.</p>'],
    ['Produto/Serviço','<p>Aulas em grupo de treino funcional limitadas a 12 alunos por turma, com planos mensais, trimestrais e semestrais, e avaliação física a cada 60 dias.</p>'],
    ['Proposta de Valor','<p><b>"Resultado físico real em 90 dias, com o acompanhamento próximo de uma equipe pequena — sem a intimidação de uma academia tradicional."</b></p>'],
    ['Mercado','<p>O mercado de estúdios de treino funcional cresceu 18% ao ano na região nos últimos três anos, impulsionado pela busca por alternativas às academias tradicionais.</p>'],
    ['Público-Alvo','<p>Adultos de 25 a 45 anos, classe média, moradores em um raio de 3 km da unidade, com renda familiar entre R$ 6 mil e R$ 15 mil e experiência prévia com academia.</p><ul><li>Já frequentaram academia e abandonaram por falta de resultado ou motivação</li><li>Valorizam comunidade e resultado visível em poucos meses</li></ul>'],
    ['Concorrentes','<p>Três concorrentes diretos foram mapeados na região:</p><table><tr><th>Concorrente</th><th>Preço</th><th>Diferencial</th><th>Fraqueza</th></tr><tr><td>FitBox Studio</td><td>R$ 249/mês</td><td>Marca forte, franquia nacional</td><td>Turmas lotadas</td></tr><tr><td>Corpo em Movimento</td><td>R$ 189/mês</td><td>Preço acessível</td><td>Estrutura defasada</td></tr><tr><td>Academia Vitalys</td><td>R$ 279/mês</td><td>Estrutura completa</td><td>Atendimento pouco personalizado</td></tr></table>'],
    ['Análise SWOT','<ul><li><b>Forças:</b> equipe técnica qualificada e comunidade engajada</li><li><b>Fraquezas:</b> dependência de um único canal de aquisição</li><li><b>Oportunidades:</b> crescimento do segmento fitness boutique</li><li><b>Ameaças:</b> entrada de novas redes na região</li></ul>'],
    ['Plano Financeiro','<p>Ponto de equilíbrio estimado em R$ 38.400/mês, equivalente a 160 alunos ativos no plano padrão (ticket médio de R$ 240 e custo fixo mensal de R$ 31.200).</p><p>Previsão de faturamento no primeiro ano: R$ 412.000, com crescimento mensal médio de 6% a partir do quarto mês.</p>'],
    ['Riscos','<ol><li>Alta concorrência no mercado de treino funcional</li><li>Dependência do Instagram como principal canal de aquisição</li><li>Necessidade de capital de giro nos primeiros seis meses</li></ol>'],
    ['Metas','<ul><li>300 alunos ativos até o mês 12</li><li>Abertura da segunda unidade até o mês 10</li><li>Redução do churn mensal de 8% para 5%</li></ul>'],
    ['Estratégia de Marketing','<p>Conteúdo orgânico diário no Instagram, parcerias com micro-influenciadores locais e aulas experimentais gratuitas como principal isca de conversão.</p><p>Canais de aquisição em ordem de prioridade: Instagram (orgânico e pago), indicação de alunos atuais e parcerias locais com nutricionistas e fisioterapeutas.</p>'],
    ['Investimento Inicial','<p>Investimento inicial estimado de R$ 186.000, distribuído entre reforma do espaço (R$ 92.000), equipamentos (R$ 61.000) e capital de giro (R$ 33.000).</p>'],
    ['VPL, TIR e Payback','<ul><li><b>VPL (36 meses):</b> R$ 214.500, a uma taxa de desconto de 15% a.a.</li><li><b>TIR:</b> 38,4% ao ano</li><li><b>Payback:</b> 17 meses</li></ul>'],
    ['Análise de Sensibilidade','<p>No cenário pessimista, com ocupação 20% abaixo do projetado, o payback se estende para 26 meses, mas o projeto permanece com VPL positivo.</p>'],
  ].map(([n,h])=>{
    const tab = makeTab(n,h);
    tab.attachment.processed = true;
    tab.attachment.extracted = 1;
    tab.attachment.processedAt = '28 ago 2026';
    tab.attachment.knowledge = stripHtml(h);
    tab.meta = deriveMeta(tab);
    return tab;
  });
  t[0].attachment.pdfName = '[THE OFFICE] SSA EV (1).pdf';
  t[0].attachment.extracted = 2;
  t[0].meta.source = t[0].attachment.pdfName;
  const sq = {'Público-Alvo':['Quem é o nosso cliente ideal?'],'Plano Financeiro':['Qual é o ponto de equilíbrio?'],
    'Concorrentes':['Quem são os nossos principais concorrentes?'],'Riscos':['Quais são os nossos principais riscos?']};
  t.forEach(x=>{ if(sq[x.name]) x.meta.suggestedQuestions=sq[x.name]; });
  return t;
}

let clients = [
  {id:1, name:'Ana Silva', email:'ana.silva@nortefit.com.br', company:'Norte Fit Studios',
   segment:'Fitness e bem-estar', version:3, planFile:'plano-negocios-v3.pdf', updated:'28 ago 2026',
   status:'Processado', lastLogin:'08 set 2026, 09:14', consultas:47, answered:43, asked:47,
   tabs:demoTabs(), tabHits:{'plano-financeiro':14,'concorrentes':11,'estrategia-de-marketing':9,'publico-alvo':8,'riscos':5},
   faq:[{q:'Qual o ponto de equilíbrio?',n:7},{q:'Quem são os concorrentes?',n:6},{q:'Qual é o público-alvo?',n:5},{q:'Quais os principais riscos?',n:4}],
   scope:{}, conversations:[{id:1,title:'Análise financeira',messages:[]},{id:2,title:'Concorrência',messages:[]}], activeConvId:1,
   queryTypes:{'Informação':22,'Dúvida':11,'Interpretação':9,'Decisão':5},
   unanswered:[{q:'Qual a política de cancelamento de planos?',theme:'Produto/Serviço'},
     {q:'Existe previsão de parceria com academias?',theme:'Estratégia de Marketing'},
     {q:'Qual o custo de aquisição por canal?',theme:'Estratégia de Marketing'},
     {q:'Como será a contratação de novos professores?',theme:'Empresa'}]},
  {id:2, name:'Rafael Matos', email:'rafael@graoecia.com.br', company:'Cafeteria Grão & Cia',
   segment:'Alimentação', version:1, planFile:'plano-graoecia.docx', updated:'02 set 2026',
   status:'Em processamento', lastLogin:'05 set 2026, 16:02', consultas:12, answered:9, asked:12,
   tabs:defaultTabs(), tabHits:{}, faq:[], scope:{}, conversations:[], activeConvId:null},
  {id:3, name:'Camila Pires', email:'camila@arquimais.com.br', company:'Estúdio Arqui+',
   segment:'Arquitetura', version:2, planFile:'plano-arqui-mais.pdf', updated:'19 ago 2026',
   status:'Processado', lastLogin:'01 set 2026, 11:40', consultas:31, answered:28, asked:31,
   tabs:defaultTabs(), tabHits:{}, faq:[], scope:{}, conversations:[], activeConvId:null},
  {id:4, name:'João Torres', email:'joao@torrescontabil.com.br', company:'Torres Contabilidade',
   segment:'Serviços contábeis', version:0, planFile:'—', updated:'—',
   status:'Pendente', lastLogin:'Nunca acessou', consultas:0, answered:0, asked:0,
   tabs:defaultTabs(), tabHits:{}, faq:[], scope:{}, conversations:[], activeConvId:null},
];

// sessionClientId = usuário logado. activeClientId = painel em uso
// (quando o admin abre o painel de outro cliente, difere de sessionClientId).
/**
 * Contas da consultoria (administradores). Ficam separadas dos clientes:
 * um administrador não tem plano próprio, ele abre o painel dos clientes.
 *
 * ATENÇÃO — versão estática: não existe servidor, então o perfil vem da
 * conta cadastrada, mas NÃO há verificação real de senha nem proteção contra
 * quem abrir o DevTools. Autenticação e autorização de verdade exigem o
 * backend (versão Java). Veja o README.
 */
const staff = [
  {id:'adm-1', role:'admin', name:'Consultoria Empresa JR', email:'consultoria@empresajr.org', lastLogin:'—'}
];

let sessionClientId = null;  // quem está logado (cliente ou administrador)
let activeClientId  = 1;     // cujo plano está aberto
let isAdmin         = false; // vem da conta, nunca de um interruptor
let adminEditing    = false; // true quando o administrador está dentro do plano de um cliente
let loginAt         = 0;     // quando a sessão começou
const SESSION_MS    = 8*60*60*1000; // a sessão vale 8 horas

function C(){ return clients.find(c=>c.id===activeClientId) || clients[0]; }
function sessionUser(){
  return staff.find(s=>s.id===sessionClientId) || clients.find(c=>c.id===sessionClientId) || clients[0];
}
function tabs(){ return C().tabs; }
function inScope(tab){
  const s = C().scope||{};
  return isAdmin ? true : (s[tab.id]!==false && tab.published!==false);
}
function visibleTabs(){ return tabs().filter(inScope); }
let activeTabId = null;
function currentTab(){
  const list = visibleTabs();
  let t = list.find(x=>x.id===activeTabId);
  if(!t){ t = list[0] || tabs()[0]; activeTabId = t ? t.id : null; }
  return t;
}

/* ============================================================
   3. AVISOS, LOGIN E LOGOUT
   ============================================================ */
/** Aviso discreto no canto da tela (substitui o alert do navegador). */
function toast(msg, kind){
  const box=document.getElementById('toasts');
  if(!box) return;
  const el=document.createElement('div');
  el.className='toast '+(kind||'info');
  el.textContent=msg;
  box.appendChild(el);
  setTimeout(()=>{ el.classList.add('out'); setTimeout(()=>el.remove(),250); }, 4500);
}
function nowLabel(){
  return today().replace('.','') + ', ' + new Date().toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'});
}
/** Mostra/esconde o que é exclusivo da consultoria, conforme o perfil da conta. */
function applyRole(){
  document.querySelectorAll('.admin-only').forEach(el=>el.classList.toggle('hidden', !isAdmin));
  document.querySelectorAll('.client-only').forEach(el=>el.classList.toggle('hidden', isAdmin));
}
function setAuthTab(which){
  ['login','forgot'].forEach(k=>{
    document.getElementById('auth-'+k).classList.toggle('hidden', which!==k);
  });
}
function loginFail(msg){
  const err=document.getElementById('login-error');
  err.textContent=msg; err.classList.remove('hidden');
}
function doLogin(){
  const email=((document.getElementById('login-email')||{}).value||'').trim().toLowerCase();
  const pass=(document.getElementById('login-pass')||{}).value||'';
  if(!email || !pass){ loginFail('Informe o e-mail de acesso e a senha.'); return; }
  const member = staff.find(x=>x.email.toLowerCase()===email) || clients.find(x=>(x.email||'').toLowerCase()===email);
  if(!member){ loginFail('Não encontramos um acesso com esse e-mail. Fale com a consultoria da Empresa JR.'); return; }
  if(member.suspended){ loginFail('Este acesso está suspenso. Fale com a consultoria da Empresa JR.'); return; }
  document.getElementById('login-error').classList.add('hidden');
  sessionClientId = member.id;
  isAdmin = member.role==='admin';
  adminEditing = isAdmin;
  activeClientId = isAdmin ? clients[0].id : member.id;
  activeTabId = null;
  member.lastLogin = nowLabel();
  loginAt = Date.now();
  document.getElementById('login-pass').value='';
  document.getElementById('view-auth').classList.add('hidden');
  document.getElementById('app').classList.remove('hidden');
  applyRole();
  updateShell();
  go(isAdmin ? 'admin' : 'dashboard');
  probeAI();
}
function doLogout(){
  document.getElementById('app').classList.add('hidden');
  document.getElementById('view-auth').classList.remove('hidden');
  isAdmin=false; adminEditing=false; sessionClientId=null; activeTabId=null;
  applyRole();
  setAuthTab('login');
  window.scrollTo(0,0);
  persist();
}

/* ============================================================
   4. PAINEL DO CLIENTE PELA CONSULTORIA
   ============================================================ */
function openClientWorkspace(id){
  if(!isAdmin) return;
  const c = clients.find(x=>x.id===id);
  if(!c) return;
  activeClientId = id;
  activeTabId = null;
  updateShell();
  go('plano');
}
function exitClientWorkspace(){
  if(!isAdmin) return;
  activeTabId = null;
  go('admin');
}
function updateShell(){
  const c=C(), u=sessionUser();
  document.getElementById('active-company').textContent = c.company;
  document.getElementById('side-user-name').textContent = u.name;
  document.getElementById('side-user-role').textContent = isAdmin ? 'Consultoria Empresa JR' : u.company;
  document.getElementById('side-avatar').textContent = initialsOf(u.name);
  document.getElementById('top-avatar').textContent = initialsOf(u.name);
  document.getElementById('imp-client-name').textContent = c.name + ' · ' + c.company;
}

/* ============================================================
   5. ROTEADOR
   ============================================================ */
let currentView='dashboard';
const pageMeta={
  dashboard:{title:'Dashboard'},
  chat:{title:'Consultar plano'},
  plano:{title:'Plano de negócios'},
  indicadores:{title:'Indicadores'},
  config:{title:'Configurações'},
  admin:{title:'Clientes & Planos'},
  conteudo:{title:'Conteúdo dos planos'},
};
function toggleSidebar(force){
  const sb=document.getElementById('sidebar'), sc=document.getElementById('sidebar-scrim');
  const open = (force===undefined) ? !sb.classList.contains('open') : !!force;
  sb.classList.toggle('open', open);
  sc.style.display = open ? 'block' : 'none';
}
const ADMIN_VIEWS=['admin','config','conteudo'];
function go(view){
  // só a consultoria entra nas telas administrativas
  if(ADMIN_VIEWS.includes(view) && !isAdmin) view='dashboard';
  // acesso suspenso enquanto a pessoa estava logada: encerra a sessão
  if(!isAdmin && sessionUser().suspended){
    doLogout(); loginFail('Seu acesso foi suspenso. Fale com a consultoria da Empresa JR.'); return;
  }
  currentView=view;
  toggleSidebar(false);
  document.querySelectorAll('.nav-item').forEach(el=>{
    const on=el.dataset.view===view;
    el.classList.toggle('active', on);
    if(on) el.setAttribute('aria-current','page'); else el.removeAttribute('aria-current');
  });
  const content=document.getElementById('content');
  content.innerHTML='';
  content.classList.toggle('chat-mode', view==='chat');
  content.appendChild(document.getElementById('tpl-'+view).content.cloneNode(true));
  document.getElementById('page-title').textContent = pageMeta[view].title;
  document.getElementById('impersonation-bar').classList.toggle('hidden', !(isAdmin && !ADMIN_VIEWS.includes(view)));
  applyRole();
  document.getElementById('page-crumb').textContent = (view==='admin'||view==='conteudo')
    ? 'Administração · Empresa JR'
    : C().company + (C().version ? ' · v'+C().version : '');
  if(view==='dashboard') initDashboard();
  if(view==='chat') initChat();
  if(view==='plano') initPlano();
  if(view==='indicadores') initIndicadores();
  if(view==='config') initConfig();
  if(view==='admin') renderAdminTable();
  if(view==='conteudo') initConteudo();
  if(view!=='indicadores') stopIndicadores();
  applyTheme(currentTheme);
  renderAiBadge();
  window.scrollTo(0,0);
}

/* ============================================================
   6. CAMADA DE IA
   ============================================================ */
let apiKey='';
let aiState='unknown';   // unknown | on | off
let AI_MODEL='claude-sonnet-4-6';
function setModel(v){ AI_MODEL=v; aiState='unknown'; renderAiBadge(); }

function setApiKey(v){ apiKey=v.trim(); aiState='unknown'; renderAiBadge(); }
function renderAiBadge(){
  const b=document.getElementById('ai-badge');
  if(!b) return;
  if(!isAdmin){ b.classList.add('hidden'); }
  const map={on:['on','IA conectada'],off:['off','IA indisponível'],unknown:['off','IA — verificando']};
  const [cls,label]=map[aiState];
  b.className='ai-badge '+cls+' admin-only'+(isAdmin?'':' hidden');
  b.textContent = aiState==='on' ? 'IA conectada' : (aiState==='off' ? 'IA offline' : 'IA');
  b.title=label;
  const s=document.getElementById('cfg-ai-state');
  if(s) s.textContent = aiState==='on' ? 'Conectada — as abas são processadas pela IA.'
    : (aiState==='off' ? 'Sem conexão. O Chat Jr usa a leitura local do PDF como alternativa.' : 'Ainda não verificada.');
}

/**
 * Chama a API de mensagens da Anthropic (Claude).
 * @param {Array} messages histórico da conversa no formato Anthropic
 * @param {string} [system] instrução de sistema
 * @param {Array} [tools] ferramentas opcionais (ex: web_search)
 * @returns {Promise<string>} texto concatenado das respostas
 */
async function callClaude(messages, system, tools, maxTokens){
  const headers={'Content-Type':'application/json'};
  if(apiKey){
    headers['x-api-key']=apiKey;
    headers['anthropic-version']='2023-06-01';
    headers['anthropic-dangerous-direct-browser-access']='true';
  }
  const body={model:AI_MODEL, max_tokens:maxTokens||1000, messages};
  if(system) body.system=system;
  if(tools) body.tools=tools;
  const res=await fetch('https://api.anthropic.com/v1/messages',{
    method:'POST', headers, body:JSON.stringify(body)
  });
  if(!res.ok){
    aiState='off'; renderAiBadge();
    throw new Error('A IA respondeu com erro '+res.status);
  }
  const data=await res.json();
  aiState='on'; renderAiBadge();
  return (data.content||[]).filter(b=>b.type==='text').map(b=>b.text).join('\n').trim();
}
function parseJson(text){
  let t=String(text||'').replace(/```json/gi,'').replace(/```/g,'').trim();
  const a=t.indexOf('{'), b=t.lastIndexOf('}');
  if(a>=0 && b>a) t=t.slice(a,b+1);
  return JSON.parse(t);
}
async function probeAI(){
  try{
    await callClaude([{role:'user',content:'Responda apenas: ok'}]);
  }catch(e){ aiState='off'; renderAiBadge(); }
}
async function testAI(){
  aiState='unknown'; renderAiBadge();
  await probeAI();
  if(aiState==='on') toast('Conexão com a IA funcionando.','ok');
  else toast('Não foi possível falar com a IA agora. O Chat Jr segue com a leitura local do PDF e a busca nas abas.','error');
}

/* --- leitura de PDF no navegador (pdf.js) --- */
let pdfjsPromise=null;
/**
 * Carrega dinamicamente a biblioteca pdf.js (via CDN) apenas quando
 * necessário, evitando peso na abertura inicial do app.
 */
function loadPdfJs(){
  if(window.pdfjsLib) return Promise.resolve(window.pdfjsLib);
  if(pdfjsPromise) return pdfjsPromise;
  pdfjsPromise=new Promise((resolve,reject)=>{
    const s=document.createElement('script');
    s.src='https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js';
    s.onload=()=>{
      window.pdfjsLib.GlobalWorkerOptions.workerSrc='https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';
      resolve(window.pdfjsLib);
    };
    s.onerror=()=>reject(new Error('pdf.js indisponível'));
    document.head.appendChild(s);
  });
  return pdfjsPromise;
}

/**
 * Extrai texto puro das primeiras 40 páginas de um PDF, no navegador.
 * @param {File} file PDF selecionado pelo usuário
 * @returns {Promise<string>} texto concatenado
 */
async function extractPdfText(file){
  const lib=await loadPdfJs();
  const buf=await file.arrayBuffer();
  const doc=await lib.getDocument({data:buf}).promise;
  const pages=Math.min(doc.numPages,40);
  let out=[];
  for(let i=1;i<=pages;i++){
    const page=await doc.getPage(i);
    const c=await page.getTextContent();
    out.push(c.items.map(it=>it.str).join(' '));
  }
  return out.join('\n\n').replace(/[ \t]+/g,' ').trim();
}
function fileToBase64(file){
  return new Promise((res,rej)=>{
    const r=new FileReader();
    r.onload=()=>res(String(r.result).split(',')[1]);
    r.onerror=()=>rej(new Error('Não foi possível ler o arquivo.'));
    r.readAsDataURL(file);
  });
}

/* ============================================================
   7. DASHBOARD
   ============================================================ */
function topTheme(c){
  const e=Object.entries(c.tabHits||{}).sort((a,b)=>b[1]-a[1])[0];
  if(!e) return '—';
  const t=c.tabs.find(x=>x.id===e[0]);
  return t ? t.name : '—';
}
function initDashboard(){
  const c=C(), u=sessionUser();
  document.getElementById('dash-hello').textContent = isAdmin
    ? 'Painel de ' + c.name
    : 'Olá, ' + u.name.split(' ')[0];
  document.getElementById('dash-sub').textContent = isAdmin
    ? 'Você está vendo o Chat Jr como ' + c.name + ' vê.'
    : 'Seu Plano de Negócios está disponível para consulta.';
  const publicadas = visibleTabs().filter(t=>t.attachment.processed||t.attachment.knowledge).length;
  if(!isAdmin && !publicadas){
    document.getElementById('dash-sub').textContent = 'Seu plano está sendo preparado pela consultoria. Assim que as primeiras etapas forem publicadas, elas aparecem aqui.';
  }
  const semResposta = Math.max(0, (c.asked||0) - (c.answered||0));
  const rows = [
    ['Última atualização', c.updated && c.updated!=='—' ? c.updated : 'Ainda não publicado'],
    ['Consultas realizadas', String(c.consultas||0)],
    ['Tema mais consultado', topTheme(c)],
    ['Perguntas sem resposta no plano', String(semResposta)]
  ];
  document.getElementById('dash-overview').innerHTML = rows.map(r=>
    `<div class="kv"><dt>${esc(r[0])}</dt><dd>${esc(r[1])}</dd></div>`).join('');
  const links = [
    ['plano','Plano de negócios','Leia cada etapa do plano organizada como um documento.'],
    ['chat','Consultar plano','Faça perguntas e veja de qual seção veio a resposta.'],
    ['indicadores','Indicadores','Veja o que mais é consultado e onde o plano pode ficar mais claro.']
  ];
  document.getElementById('dash-explore').innerHTML = links.map(l=>
    `<button class="link-row" onclick="go('${l[0]}')"><span><b>${l[1]}</b><span>${l[2]}</span></span><span class="arrow" aria-hidden="true">›</span></button>`).join('');
}
/**
 * Desenha um donut de progresso em SVG.
 * @param {number} pct valor 0–100
 * @param {string} label texto acessível (aria-label)
 * @param {string} color cor do arco preenchido
 */
function donutSvg(pct, label, color){
  const r=34, circ=2*Math.PI*r;
  const off = circ*(1-Math.max(0,Math.min(100,pct))/100);
  return `<svg viewBox="0 0 84 84" class="donut" role="img" aria-label="${label}: ${Math.round(pct)}%">
    <circle cx="42" cy="42" r="${r}" fill="none" stroke="var(--line)" stroke-width="10"/>
    <circle cx="42" cy="42" r="${r}" fill="none" stroke="${color}" stroke-width="10" stroke-linecap="round"
      stroke-dasharray="${circ.toFixed(1)}" stroke-dashoffset="${off.toFixed(1)}" transform="rotate(-90 42 42)"/>
    <text x="42" y="48" text-anchor="middle" font-size="19" font-weight="900" fill="var(--ink)"
      font-family="Roboto, sans-serif">${Math.round(pct)}%</text>
  </svg>`;
}
function askFromSuggestion(q){
  go('chat');
  setTimeout(()=>{ document.getElementById('chat-input').value=q; sendMessage(); },40);
}

/* ============================================================
   8. PLANO DE NEGÓCIOS — ABAS E ANEXOS
   ============================================================ */
function initPlano(){
  const intro=document.getElementById('plano-intro');
  if(intro) intro.textContent = isAdmin
    ? 'Cada aba tem seu próprio anexo. Envie o PDF ou o link daquela parte do plano e clique em "Processar com IA".'
    : 'Leia cada etapa do seu plano.';
  renderAttachPanel();
  renderTabList();
  renderTabContent();
}

function renderAttachPanel(){
  const tab=currentTab();
  const wrap=document.getElementById('attach-panel');
  if(!wrap) return;
  // anexos, fonte e processamento são da consultoria: o cliente nem recebe o HTML
  if(!isAdmin){ wrap.innerHTML=''; wrap.classList.add('hidden'); return; }
  wrap.classList.remove('hidden');
  if(!tab){ wrap.innerHTML='<p style="margin:0;color:var(--muted);">Nenhuma aba liberada para este acesso.</p>'; return; }
  const a=tab.attachment;
  const hasSource = !!(a.pdfName || a.slideLink);
  wrap.innerHTML = `
    <div class="aba-tag">
      <span>Anexos da aba</span><b>${esc(tab.name)}</b>
      ${a.processed ? '<span class="pill teal">Processada</span>' : '<span class="pill amber">Não processada</span>'}
    </div>
    <div class="attach-block">
      <div class="lbl2">Arquivo PDF desta aba</div>
      <div class="attach-file-row">
        <svg width="17" height="17" viewBox="0 0 20 20" fill="none"><path d="M6 2.5h6l3 3V17a.5.5 0 0 1-.5.5h-9A.5.5 0 0 1 5 17V3a.5.5 0 0 1 .5-.5Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round"/></svg>
        <span class="fn ${a.pdfName?'':'empty'}">${a.pdfName ? esc(a.pdfName) : 'Nenhum PDF anexado'}</span>
        <button class="btn" onclick="triggerPdfUpload()">${a.pdfName?'Substituir':'Anexar PDF'}</button>
      </div>
      <input type="file" id="pdf-file-input" accept=".pdf" class="hidden" onchange="onPdfSelected(this)">
      ${a.pdfName && a.pdfText ? `<div class="stat-line" style="font-size:12px;color:var(--muted);margin-top:7px;">${a.pdfText.length.toLocaleString('pt-BR')} caracteres lidos do PDF</div>`:''}
    </div>
    <div class="attach-block">
      <div class="lbl2">Link (slide, doc, planilha...)</div>
      <div class="attach-link-row">
        <input type="text" id="slide-link-input" placeholder="https://docs.google.com/..." value="${esc(a.slideLink)}">
        <button class="btn" onclick="saveSlideLink()">Salvar</button>
      </div>
      <div class="stat-line" style="font-size:12px;color:var(--muted);margin-top:7px;">Use o link quando o material não estiver em PDF.</div>
    </div>
    <div class="attach-status" id="attach-status">${attachStatusHtml(tab)}</div>`;
}
function attachStatusHtml(tab){
  const a=tab.attachment;
  const hasSource = !!(a.pdfName || a.slideLink);
  let head;
  if(a.error) head = `<div class="stat-line err">✕ ${esc(a.error)}</div>`;
  else if(a.processed) head = `<div class="stat-line ok">✓ Conteúdo processado pela IA</div>
      <div class="extract-toast"><b>${a.extracted} seção(ões) extraída(s)</b> desta aba e disponíveis para consulta no chat.</div>`;
  else head = `<div class="stat-line">○ ${hasSource ? 'Material anexado, aguardando processamento' : 'Anexe um PDF ou link para processar'}</div>`;
  return head + `
    <button class="btn yellow" style="width:100%;" onclick="processDoc()" ${hasSource?'':'disabled'}>
      ${a.processed ? 'Reprocessar com IA' : 'Processar com IA'}
    </button>`;
}
function triggerPdfUpload(){ document.getElementById('pdf-file-input').click(); }
async function onPdfSelected(input){
  if(!(input.files && input.files[0])) return;
  const file=input.files[0];
  const tab=currentTab();
  const a=tab.attachment;
  a.pdfName=file.name; a.error=''; a.pdfText=''; a.pdfData=null;
  renderAttachPanel();
  const status=document.getElementById('attach-status');
  status.innerHTML=`<div class="stat-line"><div class="typing-dots"><span></span><span></span><span></span></div> Lendo o PDF...</div>`;
  try{
    a.pdfText = await extractPdfText(file);
  }catch(e){
    a.pdfText='';
    try{ a.pdfData = await fileToBase64(file); }catch(_){}
  }
  if(!a.pdfText){
    try{ a.pdfData = a.pdfData || await fileToBase64(file); }catch(_){}
  }
  refreshTabViews();
}
function saveSlideLink(){
  const el=document.getElementById('slide-link-input');
  currentTab().attachment.slideLink = el.value.trim();
  currentTab().attachment.error='';
  refreshTabViews();
}

/* --- Processar com IA --------------------------------------- */
async function processDoc(){
  const tab=currentTab();
  const a=tab.attachment;
  const status=document.getElementById('attach-status');
  a.error='';
  status.innerHTML=`<div class="stat-line"><div class="typing-dots"><span></span><span></span><span></span></div> Analisando o material da aba "${esc(tab.name)}"...</div>`;

  let result=null, usedAI=false;
  try{
    result = await aiAnalyzeTab(tab);
    usedAI = true;
  }catch(e){
    result = localAnalyzeTab(tab, e && e.message);
  }

  if(result){
    tab.html = result.html;
    if(result.titulo) tab.title = result.titulo;
    a.processed = true;
    a.sections = result.sections || [];
    a.extracted = Math.max(1, a.sections.length);
    a.knowledge = stripHtml(result.html);
    a.processedAt = today();
    a.error = usedAI ? '' : (result.note||'');
    // só sobrescreve o que a IA realmente trouxe; edições manuais de outros campos ficam
    const rm = result.meta || {};
    tab.meta = Object.assign(emptyMeta(), tab.meta||{});
    ['shortDescription','whatIsIt','objective'].forEach(k=>{ if(rm[k]) tab.meta[k]=rm[k]; });
    ['keyPoints','suggestedQuestions'].forEach(k=>{ if(rm[k] && rm[k].length) tab.meta[k]=rm[k]; });
    tab.meta.source = a.pdfName || a.slideLink || tab.meta.source;
    tab.meta.updatedAt = a.processedAt;
    tab.published = true;
    const c=C();
    c.updated = today();
    c.status = 'Processado';
    if(a.pdfName) c.planFile = a.pdfName;
  }
  refreshTabViews();
}
/** Redesenha tudo que mostra a aba atual (plano e área de conteúdo). */
function refreshTabViews(){
  renderAttachPanel(); renderTabList(); renderTabContent();
  if(currentView==='conteudo') renderContentAdmin();
}

/**
 * Pede à IA que transforme o material anexado (PDF ou link) no
 * conteúdo textual de uma aba do Plano de Negócios.
 * @param {object} tab aba de destino
 * @returns {Promise<{titulo:string,html:string,sections:string[]}>}
 */
async function aiAnalyzeTab(tab){
  const a=tab.attachment;
  const c=C();
  const system = [
    'Você é analista de negócios da Empresa JR, consultoria empresarial.',
    'Sua tarefa: transformar o material anexado a uma aba de um Plano de Negócios em texto de plano, claro e objetivo, em português do Brasil.',
    'Use somente informações presentes no material. Não invente números, nomes ou fatos.',
    'Se o material não trouxer informação sobre a aba, diga isso explicitamente no texto.',
    'Responda SOMENTE com um objeto JSON válido, sem markdown e sem comentários, no formato:',
    '{"titulo":"...","descricaoCurta":"...","oQueE":"...","objetivo":"...","html":"...","pontosPrincipais":["..."],"perguntasSugeridas":["..."],"sections":["..."]}',
    'Em "html" use apenas as tags <p>, <b>, <ul>, <ol>, <li>, <h4> e <table>/<tr>/<th>/<td>. Entre 150 e 450 palavras.',
    '"descricaoCurta": uma frase. "oQueE": o que esta etapa do plano trata. "objetivo": para que ela serve neste plano.',
    '"pontosPrincipais": de 3 a 5 itens extraídos do material. "perguntasSugeridas": de 2 a 4 perguntas que o texto consegue responder.',
    'Se o material não sustentar um campo, devolva "" ou [] nesse campo. Nunca preencha com suposições.',
    'Em "sections" liste os títulos das seções que você identificou no material.'
  ].join(' ');

  const instr = [
    'Empresa: ' + c.company + ' (' + c.segment + ').',
    'Aba do Plano de Negócios a ser escrita: "' + tab.name + '".',
    a.slideLink ? 'Link informado pelo cliente: ' + a.slideLink : '',
    'Escreva o conteúdo dessa aba a partir do material abaixo, mantendo os números exatamente como aparecem.',
    a.pdfText ? '\n--- MATERIAL ANEXADO ---\n' + a.pdfText.slice(0,24000) : ''
  ].filter(Boolean).join('\n');

  let content;
  if(!a.pdfText && a.pdfData){
    content = [
      {type:'document', source:{type:'base64', media_type:'application/pdf', data:a.pdfData}},
      {type:'text', text:instr}
    ];
  } else {
    content = instr;
  }
  const tools = (!a.pdfText && !a.pdfData && a.slideLink) ? [{type:'web_search_20250305', name:'web_search'}] : null;
  const raw = await callClaude([{role:'user', content}], system, tools, 3000);
  const json = parseJson(raw);
  if(!json.html) throw new Error('Resposta da IA sem conteúdo.');
  const list = v=>Array.isArray(v)?v.map(x=>String(x).trim()).filter(Boolean).slice(0,6):[];
  return {titulo: json.titulo || tab.name, html: sanitizeHtml(json.html), sections: Array.isArray(json.sections)?json.sections:[],
    meta:{shortDescription:String(json.descricaoCurta||'').trim(), whatIsIt:String(json.oQueE||'').trim(),
          objective:String(json.objetivo||'').trim(), keyPoints:list(json.pontosPrincipais),
          suggestedQuestions:list(json.perguntasSugeridas)}};
}

/**
 * Alternativa local, sem IA: monta um resumo extrativo a partir do
 * texto lido do PDF, escolhendo frases relevantes e destacando números.
 */
function localAnalyzeTab(tab, reason){
  const a=tab.attachment;
  const note = 'Processado localmente, sem IA' + (reason ? ' ('+reason+')' : '') + '.';
  if(a.pdfText && a.pdfText.length>200){
    const clean=a.pdfText.replace(/\s+/g,' ').trim();
    const sentences=clean.split(/(?<=[.!?])\s+/).filter(s=>s.length>40);
    const key=tab.name.toLowerCase().split(/[^a-zà-ú]+/).filter(w=>w.length>3);
    const scored=sentences.map(s=>{
      const l=s.toLowerCase();
      let sc=key.reduce((n,k)=>n+(l.includes(k)?3:0),0);
      if(/r\$|%|\d{2,}/.test(l)) sc+=2;
      return {s,sc};
    }).sort((x,y)=>y.sc-x.sc);
    const top=scored.slice(0,6).map(x=>x.s);
    const numbers=[...new Set(clean.match(/R\$ ?[\d.,]+|\d+(?:,\d+)?%/g)||[])].slice(0,8);
    let html='<p>'+esc(top.slice(0,3).join(' '))+'</p>';
    if(top.length>3) html+='<h4>Outros pontos do material</h4><ul>'+top.slice(3).map(s=>'<li>'+esc(s)+'</li>').join('')+'</ul>';
    if(numbers.length) html+='<h4>Números citados</h4><ul>'+numbers.map(n=>'<li>'+esc(n)+'</li>').join('')+'</ul>';
    html+='<p style="color:var(--muted);font-size:13.5px;">'+esc(note)+' Conecte a IA em Configurações para uma redação completa desta aba.</p>';
    return {titulo:tab.name, html, sections:['Trechos principais','Números citados'], note};
  }
  const fonte = a.pdfName ? 'o arquivo <b>'+esc(a.pdfName)+'</b>' : 'o link <b>'+esc(a.slideLink)+'</b>';
  return {
    titulo: tab.name,
    html: '<p>Não foi possível ler '+fonte+' automaticamente agora.</p><p style="color:var(--muted);">'+esc(note)+' Verifique a conexão com a IA em Configurações, ou escreva o conteúdo desta aba manualmente pelo botão de editar.</p>',
    sections: [],
    note
  };
}

/* --- lista de abas ---------------------------------------- */
function renderTabList(){
  const wrap=document.getElementById('plan-cat-list');
  if(!wrap) return;
  wrap.innerHTML='';
  const header=document.createElement('div');
  header.className='plan-cat-header';
  header.innerHTML=`<span>${isAdmin?'ABAS':'ETAPAS'}</span>` + (isAdmin ? `<button class="btn blue" style="padding:6px 11px;font-size:12px;" onclick="openAddTabModal()">+ Nova aba</button>` : '');
  wrap.appendChild(header);

  const list=visibleTabs();
  const inner=document.createElement('div');
  list.forEach(t=>{
    const a=t.attachment;
    const div=document.createElement('div');
    div.className='plan-cat-item'+(t.id===activeTabId?' active':'');
    div.innerHTML=`
      <span class="cat-name">${esc(t.name)}</span>
      ${isAdmin ? `<span class="cat-item-actions">
        ${(a.pdfName||a.slideLink) ? `<span class="cat-attach-dot ${a.processed?'':'pending'}" title="${a.processed?'Anexo processado':'Anexo aguardando processamento'}"></span>`:''}
        <button class="cat-icon-btn" title="Editar aba" aria-label="Editar aba ${esc(t.name)}" onclick="openEditTabModal('${t.id}',event)">
          <svg width="13" height="13" viewBox="0 0 20 20" fill="none"><path d="M14.2 2.8a1.6 1.6 0 0 1 2.3 2.3L7 14.6l-3 .7.7-3 9.5-9.5Z" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"/></svg>
        </button>
        <button class="cat-icon-btn del" title="Excluir aba" aria-label="Excluir aba ${esc(t.name)}" onclick="deleteTab('${t.id}',event)">
          <svg width="13" height="13" viewBox="0 0 20 20" fill="none"><path d="M4.5 6h11M8 6V4.5a1 1 0 0 1 1-1h2a1 1 0 0 1 1 1V6m-6.5 0 .6 9a1 1 0 0 0 1 .9h5.8a1 1 0 0 0 1-.9l.6-9" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round"/></svg>
        </button>
      </span>` : ''}`;
    div.onclick=()=>{
      activeTabId=t.id;
      const si=document.getElementById('plan-search-input'); if(si) si.value='';
      renderTabList(); renderAttachPanel(); renderTabContent();
    };
    inner.appendChild(div);
  });
  wrap.appendChild(inner);
}
function stageStatus(t){
  const a=t.attachment, src=!!(a.pdfName||a.slideLink);
  if(a.processed && t.published!==false) return {label:'Publicado', cls:'teal'};
  if(a.processed) return {label:'Não publicado', cls:'amber'};
  if(src) return {label:'Aguardando processamento', cls:'amber'};
  return {label:'Sem material', cls:'gray'};
}
function stageSection(title, inner){ return `<section class="stage-sec"><h3>${esc(title)}</h3>${inner}</section>`; }
function renderTabContent(){
  const wrap=document.getElementById('plan-content-wrap');
  if(!wrap) return;
  const t=currentTab();
  if(!t){ wrap.innerHTML=''; return; }
  const a=t.attachment, m=Object.assign(emptyMeta(), t.meta||{});
  const head=`<div class="eyebrow">Plano de negócios · ${esc(C().company)}</div><h2>${esc(t.title||t.name)}</h2>`;
  if(!isAdmin && !a.processed && !a.knowledge){
    wrap.innerHTML=`<article class="card plan-content">${head}<p class="empty-note">Esta etapa ainda não foi publicada pela consultoria. Assim que estiver disponível, ela aparece aqui.</p></article>`;
    return;
  }
  const upd = a.processedAt || m.updatedAt || C().updated;
  let parts='';
  if(m.shortDescription && m.shortDescription!==m.whatIsIt) parts+=`<p class="stage-lead">${esc(m.shortDescription)}</p>`;
  if(m.whatIsIt) parts+=stageSection('O que é esta etapa?',`<p>${esc(m.whatIsIt)}</p>`);
  if(m.objective) parts+=stageSection('Objetivo',`<p>${esc(m.objective)}</p>`);
  parts+=stageSection('Conteúdo',`<div class="body-text">${t.html || emptyTabHtml()}</div>`);
  if(m.keyPoints.length) parts+=stageSection('Principais pontos',`<ul class="stage-points">${m.keyPoints.map(k=>`<li>${esc(k)}</li>`).join('')}</ul>`);
  const adminNote = isAdmin
    ? `<p class="stage-admin-note">${a.processed?'Extraído pela IA · ':''}${t.published===false?'Rascunho: o cliente ainda não vê esta etapa. ':''}${a.processed?'':'Sem conteúdo processado · anexe o PDF ou o link desta aba acima.'}</p>` : '';
  wrap.innerHTML=`<article class="card plan-content">${head}${adminNote}${parts}<p class="stage-updated">Atualizado em ${esc(upd)}</p></article>`;
}
function planSearch(term){
  const wrap=document.getElementById('plan-content-wrap');
  term=String(term||'').trim().toLowerCase();
  if(!term){ renderTabContent(); return; }
  const results=visibleTabs().filter(t=>(t.name+' '+stripHtml(t.html)).toLowerCase().includes(term));
  if(!results.length){
    wrap.innerHTML=`<div class="card" style="padding:26px;color:var(--muted);font-size:14px;">Nada encontrado para "<b>${esc(term)}</b>". Tente outra palavra, ou processe as abas que ainda não têm conteúdo.</div>`;
    return;
  }
  wrap.innerHTML='<div class="plan-search-results"></div>';
  const rw=wrap.querySelector('.plan-search-results');
  results.forEach((t,i)=>{
    const plain=stripHtml(t.html);
    const idx=plain.toLowerCase().indexOf(term);
    const start=Math.max(0, idx-70);
    let snip=esc(plain.slice(start, idx+term.length+110).trim());
    snip=snip.replace(new RegExp('('+term.replace(/[.*+?^${}()|[\]\\]/g,'\\$&')+')','ig'),'<mark>$1</mark>');
    const div=document.createElement('div');
    div.className='card result-item';
    div.innerHTML=`<div class="sec">${esc(t.name)}</div><div class="snip">…${snip}…</div><div class="rel">Relevância ${Math.max(40,95-i*11)}%</div>`;
    div.onclick=()=>{ activeTabId=t.id; document.getElementById('plan-search-input').value=''; renderTabList(); renderAttachPanel(); renderTabContent(); };
    rw.appendChild(div);
  });
}

/* --- criar / editar / excluir aba -------------------------- */
let tabModalMode='add', tabModalEditingId=null;
function openAddTabModal(){
  tabModalMode='add'; tabModalEditingId=null;
  document.getElementById('tab-modal-title').textContent='Nova aba';
  document.getElementById('tab-modal-sub').textContent='A aba nasce com os campos de PDF e de link prontos, iguais aos das outras abas.';
  document.getElementById('tab-name-input').value='';
  document.getElementById('tab-content-input').value='';
  openModal('tab-modal');
}
function openEditTabModal(id,ev){
  if(ev) ev.stopPropagation();
  const t=tabs().find(x=>x.id===id);
  if(!t) return;
  tabModalMode='edit'; tabModalEditingId=id;
  document.getElementById('tab-modal-title').textContent='Editar aba';
  document.getElementById('tab-modal-sub').textContent='Altere o nome ou escreva o conteúdo desta aba manualmente.';
  document.getElementById('tab-name-input').value=t.name;
  document.getElementById('tab-content-input').value=stripHtml(t.html).startsWith('Esta aba ainda não tem conteúdo')?'':t.html.replace(/<\/p>\s*<p>/g,'\n\n').replace(/<br\s*\/?>/g,'\n').replace(/<[^>]+>/g,'').trim();
  openModal('tab-modal');
}
function saveTabModal(){
  const name=document.getElementById('tab-name-input').value.trim();
  if(!name){ toast('Dê um nome para a aba.','error'); return; }
  const raw=document.getElementById('tab-content-input').value.trim();
  const html = raw ? textToHtml(raw) : emptyTabHtml();
  const list=tabs();
  if(tabModalMode==='add'){
    let base=slugify(name), id=base, i=2;
    while(list.some(t=>t.id===id)){ id=base+'-'+i; i++; }
    const t=makeTab(name, html);
    t.id=id;
    if(raw){ t.attachment.knowledge=stripHtml(html); }
    list.push(t);
    activeTabId=id;
  }else{
    const t=list.find(x=>x.id===tabModalEditingId);
    if(t){
      t.name=name; t.title=name; t.html=html;
      if(raw){ t.attachment.knowledge=stripHtml(html); t.attachment.processed=true; t.attachment.processedAt=today(); t.attachment.extracted=Math.max(1,t.attachment.extracted); }
      activeTabId=t.id;
    }
  }
  closeModal('tab-modal');
  const si=document.getElementById('plan-search-input'); if(si) si.value='';
  renderTabList(); renderAttachPanel(); renderTabContent();
}
function deleteTab(id,ev){
  if(ev) ev.stopPropagation();
  const list=tabs();
  if(list.length<=1){ toast('O plano precisa de pelo menos uma aba.','error'); return; }
  const t=list.find(x=>x.id===id);
  if(!t) return;
  if(!confirm('Excluir a aba "'+t.name+'"? O conteúdo e os anexos dela serão perdidos.')) return;
  C().tabs = list.filter(x=>x.id!==id);
  if(activeTabId===id) activeTabId=null;
  renderTabList(); renderAttachPanel(); renderTabContent();
}

/* ============================================================
   9. CHAT — CONSULTA AO PLANO
   ============================================================ */
function convs(){ return C().conversations; }
function currentConv(){ return convs().find(c=>c.id===C().activeConvId); }
function initChat(){
  const c=C();
  const processed=c.tabs.filter(t=>t.attachment.processed).length;
  const sub=document.getElementById('chat-head-sub');
  if(sub) sub.textContent = (isAdmin && !processed)
    ? 'Nenhuma aba processada ainda. Anexe o material em Plano de negócios e clique em "Processar com IA".'
    : 'Faça uma pergunta sobre seu Plano de Negócios.';
  if(!convs().length){ convs().push({id:Date.now(), title:'Nova conversa', messages:[]}); }
  if(!currentConv()) c.activeConvId = convs()[0].id;
  renderConvList();
  renderThread();
}
let convFilter='';
function filterConvs(v){ convFilter=String(v||'').toLowerCase(); renderConvList(); }
function renderConvList(){
  const list=document.getElementById('conv-list');
  if(!list) return;
  list.innerHTML='';
  convs().filter(c=>!convFilter || c.title.toLowerCase().includes(convFilter)).forEach(c=>{
    const div=document.createElement('div');
    div.className='conv-item'+(c.id===C().activeConvId?' active':'');
    div.innerHTML=`<span class="dot"></span><span class="t">${esc(c.title)}</span>
      <button class="kebab" title="Renomear" onclick="event.stopPropagation();renameConv(${c.id})">
        <svg width="14" height="14" viewBox="0 0 20 20" fill="none"><path d="M4 16v-2.5L13.5 4l2.5 2.5L6.5 16H4Z" stroke="currentColor" stroke-width="1.4" stroke-linejoin="round"/></svg>
      </button>
      <button class="kebab" title="Excluir" onclick="event.stopPropagation();deleteConv(${c.id})">
        <svg width="14" height="14" viewBox="0 0 20 20" fill="none"><path d="M5 6h10M8.5 6V4.5h3V6M6 6l.6 9.5h6.8L14 6" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round"/></svg>
      </button>`;
    div.onclick=()=>{ C().activeConvId=c.id; renderConvList(); renderThread(); };
    list.appendChild(div);
  });
}
function renameConv(id){
  const c=convs().find(x=>x.id===id);
  const n=prompt('Renomear conversa:', c.title);
  if(n){ c.title=n; renderConvList(); }
}
function deleteConv(id){
  if(!confirm('Excluir esta conversa?')) return;
  C().conversations = convs().filter(x=>x.id!==id);
  if(C().activeConvId===id) C().activeConvId = convs().length ? convs()[0].id : null;
  if(!convs().length) initChat(); else { renderConvList(); renderThread(); }
}
function newConversation(){
  const id=Date.now();
  convs().unshift({id, title:'Nova conversa', messages:[]});
  C().activeConvId=id;
  renderConvList(); renderThread();
}
/** Perguntas sugeridas: as das etapas publicadas; senão, as padrão. */
function chatSuggestions(){
  const own=[]; visibleTabs().forEach(t=>((t.meta&&t.meta.suggestedQuestions)||[]).forEach(q=>{ if(!own.includes(q)) own.push(q); }));
  const base=['Qual é o nosso público-alvo?','Quais são os nossos principais riscos?','Qual é o ponto de equilíbrio?','Quem são os nossos principais concorrentes?'];
  return (own.length>=2 ? own : base).slice(0,4);
}
function renderThread(){
  const thread=document.getElementById('chat-thread');
  if(!thread) return;
  const conv=currentConv();
  thread.innerHTML='';
  if(!conv || !conv.messages.length){
    thread.innerHTML=`
      <div class="chat-empty">
        <h2>Faça uma pergunta sobre o plano da ${esc(C().company)}</h2>
        <p>As respostas vêm somente do conteúdo do plano e indicam a seção usada. Quando a informação não está no plano, o chat avisa em vez de inventar.</p>
        <div class="suggest-grid">${chatSuggestions().map(q=>`<button class="suggest-chip" onclick="askFromSuggestion(${JSON.stringify(q).replace(/"/g,'&quot;')})"><span>${esc(q)}</span><span class="arrow" aria-hidden="true">›</span></button>`).join('')}</div>
      </div>`;
    return;
  }
  conv.messages.forEach(m=>thread.appendChild(renderMessage(m)));
  thread.scrollTop=thread.scrollHeight;
}
function renderMessage(m){
  const row=document.createElement('div');
  row.className='msg-row '+m.role;
  const av = m.role==='ai' ? `<img src="${LOGO_SRC}" alt="Chat Jr">` : esc(initialsOf(sessionUser().name));
  const src = m.source ? `<div class="msg-source">📄 <span><b>Fonte no plano:</b> ${esc(m.source)}</span></div>` : '';
  const tag = m.inference ? '<div class="msg-infer-tag">Inferência</div>' : '';
  const note = m.degraded ? '<div class="msg-note">O assistente está temporariamente indisponível. Esta resposta veio de uma busca direta no texto do plano.</div>' : '';
  row.innerHTML=`<div class="msg-avatar">${av}</div><div class="msg-bubble">${tag}${m.html}${src}${note}</div>`;
  return row;
}
function autoGrow(el){ el.style.height='auto'; el.style.height=Math.min(el.scrollHeight,130)+'px'; }
function handleChatKey(e){ if(e.key==='Enter' && !e.shiftKey){ e.preventDefault(); sendMessage(); } }

/**
 * Concatena o texto de todas as abas visíveis (base de conhecimento
 * que a IA recebe como contexto do plano).
 */
function knowledgeBase(){
  return visibleTabs().map(t=>{
    const text = t.attachment.knowledge || stripHtml(t.html);
    if(!text || text.startsWith('Esta aba ainda não tem conteúdo')) return null;
    return '### ' + t.name + '\n' + text.slice(0,1600);
  }).filter(Boolean).join('\n\n');
}
async function sendMessage(){
  const input=document.getElementById('chat-input');
  const text=input.value.trim();
  if(!text) return;
  const conv=currentConv();
  if(!conv) return;
  conv.messages.push({role:'user', html:'<p>'+esc(text)+'</p>'});
  if(conv.title==='Nova conversa') conv.title=text.slice(0,32);
  input.value=''; autoGrow(input);
  renderConvList(); renderThread();

  const thread=document.getElementById('chat-thread');
  const typing=document.createElement('div');
  typing.className='msg-row ai';
  typing.innerHTML=`<div class="msg-avatar"><img src="${LOGO_SRC}" alt=""></div><div class="msg-bubble"><div class="typing-dots"><span></span><span></span><span></span></div></div>`;
  thread.appendChild(typing);
  thread.scrollTop=thread.scrollHeight;

  let answer;
  try{ answer = await aiAnswer(text, conv); }
  catch(e){ answer = localAnswer(text); answer.degraded = true; }

  typing.remove();
  conv.messages.push({role:'ai', html:answer.html, source:answer.source, inference:answer.inference, degraded:!!answer.degraded});
  registerQuery(text, answer);
  renderThread();
}
async function aiAnswer(question, conv){
  const kb=knowledgeBase();
  if(!kb) return {html: isAdmin
    ? '<p>Nenhuma aba do plano foi processada ainda. Vá em <b>Plano de negócios</b>, anexe o PDF ou o link de uma aba e clique em "Processar com IA".</p>'
    : '<p>O plano ainda não foi publicado pela consultoria. Assim que as etapas estiverem disponíveis, você poderá consultá-las aqui.</p>'};
  const system = [
    'Você é o Chat Jr, assistente da Empresa JR que responde perguntas sobre o Plano de Negócios de um cliente.',
    'Responda em português do Brasil, apenas com base no PLANO fornecido.',
    'Se a resposta não estiver no plano, responda que a informação não está no plano e sugira o que seria necessário registrar.',
    'Quando a resposta exigir interpretar ou comparar informações do plano em vez de citá-las, marque "inferencia" como true.',
    'Responda SOMENTE com JSON válido, sem markdown, no formato:',
    '{"html":"...","fonte":"nome da aba","inferencia":false,"tipo":"Informação"}',
    '"tipo" classifica a pergunta como exatamente uma destas: Informação, Dúvida, Interpretação ou Decisão. Se a resposta não estiver no plano, deixe "fonte" vazio.',
    'Em "html" use apenas <p>, <b>, <ul>, <ol>, <li> e <table>/<tr>/<th>/<td>. Seja direto: no máximo 200 palavras.'
  ].join(' ');
  const history = conv.messages.slice(-6,-1).map(m=>({role: m.role==='ai'?'assistant':'user', content: stripHtml(m.html).slice(0,600)}));
  const msgs = [
    {role:'user', content:'PLANO DE NEGÓCIOS DA EMPRESA ' + C().company + ':\n\n' + kb.slice(0,26000)},
    {role:'assistant', content:'Plano recebido. Pode perguntar.'},
    ...history,
    {role:'user', content: question}
  ];
  const raw = await callClaude(msgs, system);
  const j = parseJson(raw);
  return {html: sanitizeHtml(j.html) || '<p>Não consegui formular a resposta.</p>', source: j.fonte || '', inference: !!j.inferencia,
          type: QUERY_TYPES.includes(j.tipo) ? j.tipo : ''};
}
function localAnswer(question){
  const q=question.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'');
  const words=q.split(/[^a-z0-9]+/).filter(w=>w.length>3);
  const scored=visibleTabs().map(t=>{
    const hay=(t.name+' '+(t.attachment.knowledge||stripHtml(t.html))).toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'');
    if(!hay || hay.includes('esta aba ainda nao tem conteudo')) return {t,sc:0};
    let sc=words.reduce((n,w)=>n+(hay.includes(w)?1:0),0);
    if(hay.startsWith(q.slice(0,12))) sc+=1;
    return {t,sc};
  }).sort((a,b)=>b.sc-a.sc);
  const best=scored[0];
  if(!best || best.sc===0){
    return {html:'<p>Não encontrei essa informação no seu Plano de Negócios. Se quiser, posso ajudar a identificar quais dados precisariam ser registrados para responder isso.</p>'};
  }
  return {html: best.t.html, source: best.t.name};
}
const QUERY_TYPES=['Informação','Dúvida','Interpretação','Decisão'];
/** Classificação simples por palavras-chave, usada quando a IA não informa o tipo. */
function classifyQuery(q){
  const t=String(q||'').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'');
  if(/\b(devo|devemos|vale a pena|recomend|deveria|priorizar|melhor opcao|escolher)\b/.test(t)) return 'Decisão';
  if(/\b(resum|compar|explic|significa|interpret|o que isso|impacto|por que)\b/.test(t)) return 'Interpretação';
  if(/\b(como|sera que|posso|consigo|e possivel|duvida)\b/.test(t)) return 'Dúvida';
  return 'Informação';
}
/** Tema (etapa) mais próximo de uma pergunta, pelo nome e pelo começo do texto da etapa. */
function guessTheme(q){
  const norm=x=>String(x||'').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g,'');
  const words=norm(q).split(/[^a-z0-9]+/).filter(w=>w.length>3);
  let best=null, bs=0;
  tabs().forEach(t=>{
    const hay=norm(t.name)+' '+norm((t.attachment.knowledge||stripHtml(t.html)).slice(0,400));
    const sc=words.reduce((n,w)=>n+(norm(t.name).includes(w)?3:(hay.includes(w)?1:0)),0);
    if(sc>bs){ bs=sc; best=t; }
  });
  return best ? best.name : 'Sem tema definido';
}
function registerQuery(question, answer){
  const c=C();
  c.consultas++; c.asked++;
  c.queryTypes=c.queryTypes||{}; c.unanswered=c.unanswered||[];
  const ty=answer.type||classifyQuery(question);
  c.queryTypes[ty]=(c.queryTypes[ty]||0)+1;
  if(answer.source) c.answered++;
  else {
    c.unanswered.unshift({q:question.trim().slice(0,120), theme:guessTheme(question)});
    c.unanswered=c.unanswered.slice(0,30);
  }
  if(answer.source){
    const t=visibleTabs().find(x=>answer.source.indexOf(x.name)>=0);
    if(t) c.tabHits[t.id]=(c.tabHits[t.id]||0)+1;
  }
  const key=question.trim();
  const f=c.faq.find(x=>x.q.toLowerCase()===key.toLowerCase());
  if(f) f.n++; else c.faq.unshift({q:key.slice(0,60), n:1});
  c.faq=c.faq.sort((a,b)=>b.n-a.n).slice(0,6);
}

/* ============================================================
   10. INDICADORES
   ============================================================ */
let indInterval=null;
function initIndicadores(){ renderIndicadores(); stopIndicadores(); }
function stopIndicadores(){ if(indInterval){ clearInterval(indInterval); indInterval=null; } }
function pct(n,t){ return t ? Math.round(n/t*100) : 0; }
function renderIndicadores(){
  const sum=document.getElementById('ind-summary');
  if(!sum) return;
  const c=C();
  const semResp=Math.max(0,(c.asked||0)-(c.answered||0));
  const rows=[
    ['Consultas realizadas', String(c.consultas||0)],
    ['Respostas encontradas no plano', c.asked ? c.answered+' de '+c.asked+' ('+pct(c.answered,c.asked)+'%)' : '—'],
    ['Perguntas sem resposta no plano', c.asked ? semResp+' ('+pct(semResp,c.asked)+'%)' : '—']
  ];
  sum.innerHTML=rows.map(r=>`<div class="kv"><dt>${esc(r[0])}</dt><dd>${esc(r[1])}</dd></div>`).join('');

  // temas: participação de cada etapa nas respostas com fonte
  const hits=Object.entries(c.tabHits||{}).map(([id,n])=>{
    const t=c.tabs.find(x=>x.id===id); return {label:t?t.name:id, val:n};
  }).sort((a,b)=>b.val-a.val).slice(0,6);
  const total=Object.values(c.tabHits||{}).reduce((a,b)=>a+b,0);
  const bars=document.getElementById('ind-bars');
  bars.innerHTML = hits.length
    ? hits.map(h=>`<div class="bar-row"><div class="lbl">${esc(h.label)}</div>
        <div class="bar-track" role="img" aria-label="${esc(h.label)}: ${pct(h.val,total)}%"><div class="bar-fill" style="width:${pct(h.val,total)}%;"></div></div>
        <div class="val">${pct(h.val,total)}%</div></div>`).join('')
    : '<p class="empty-note" style="margin:0;">Ainda não há consultas registradas. Os temas mais perguntados aparecem aqui.</p>';

  const faq=document.getElementById('ind-faq');
  faq.innerHTML = c.faq.length
    ? c.faq.map(f=>`<div class="faq-item"><span>${esc(f.q)}</span><span class="q-count">${f.n}×</span></div>`).join('')
    : '<p class="empty-note" style="margin:0;">Nenhuma pergunta registrada ainda.</p>';

  const qt=c.queryTypes||{}, qtotal=QUERY_TYPES.reduce((n,k)=>n+(qt[k]||0),0);
  document.getElementById('ind-types').innerHTML = qtotal
    ? QUERY_TYPES.map(k=>`<div class="bar-row"><div class="lbl">${k}</div>
        <div class="bar-track" role="img" aria-label="${k}: ${pct(qt[k]||0,qtotal)}%"><div class="bar-fill" style="width:${pct(qt[k]||0,qtotal)}%;"></div></div>
        <div class="val">${pct(qt[k]||0,qtotal)}%</div></div>`).join('')
    : '<p class="empty-note" style="margin:0;">Sem consultas para classificar ainda.</p>';

  const gaps=c.unanswered||[];
  document.getElementById('ind-gap-sub').textContent = gaps.length
    ? gaps.length+(gaps.length===1?' pergunta não encontrou':' perguntas não encontraram')+' informação suficiente no plano. Elas indicam o que vale detalhar nas próximas entregas.'
    : 'Todas as perguntas feitas até agora foram respondidas a partir do plano.';
  document.getElementById('ind-gaps').innerHTML = gaps.length
    ? '<div class="gap-list">'+gaps.slice(0,10).map(g=>`<div class="gap-item"><span>${esc(g.q)}</span><span class="gap-theme">${esc(g.theme)}</span></div>`).join('')+'</div>'
    : '';
}

/* ============================================================
   11. CONFIGURAÇÕES
   ============================================================ */
function initConfig(){
  const u=sessionUser(), c=C();
  document.getElementById('cfg-name').value=u.name;
  document.getElementById('cfg-email').value=u.email;
  document.getElementById('cfg-company').value=c.company;
  document.getElementById('cfg-segment').value=c.segment;
  document.getElementById('cfg-api-key').value=apiKey;
  document.getElementById('cfg-model').value=AI_MODEL;
  const st=document.getElementById('cfg-storage');
  if(st) st.textContent=storageInfo();
  document.getElementById('theme-settings-switch').classList.toggle('on', currentTheme==='dark');
  renderAiBadge();
}
function saveProfile(){
  const u=sessionUser();
  u.name=document.getElementById('cfg-name').value.trim()||u.name;
  u.email=document.getElementById('cfg-email').value.trim()||u.email;
  updateShell();
  toast('Perfil salvo.','ok');
}
function saveCompany(){
  const c=C();
  c.company=document.getElementById('cfg-company').value.trim()||c.company;
  c.segment=document.getElementById('cfg-segment').value.trim()||c.segment;
  updateShell();
  document.getElementById('page-crumb').textContent=c.company+(c.version?' · v'+c.version:'');
  toast('Dados da empresa salvos.','ok');
}

/* ============================================================
   12. ADMIN — CLIENTES E PLANOS
   ============================================================ */
function renderAdminTable(){
  const tb=document.getElementById('admin-tbody');
  if(!tb) return;
  tb.innerHTML='';
  clients.forEach(c=>{
    const pill=c.suspended?'amber':(c.status==='Processado'?'teal':(c.status==='Pendente'?'amber':'blue'));
    const stLabel=c.suspended?'Acesso suspenso':c.status;
    const tr=document.createElement('tr');
    tr.innerHTML=`
      <td><div class="client-cell"><div class="mini-avatar" aria-hidden="true">${esc(initialsOf(c.name))}</div>
        <span class="nm"><b>${esc(c.name)}</b></span></div></td>
      <td data-label="Empresa">${esc(c.company)}</td>
      <td data-label="E-mail de acesso">${esc(c.email)}</td>
      <td data-label="Plano">${c.version?'v'+c.version+' · ':''}${esc(c.planFile)}</td>
      <td data-label="Status"><span class="pill ${pill}">${esc(stLabel)}</span></td>
      <td data-label="Último acesso">${esc(c.lastLogin)}</td>
      <td><div class="row-actions">
        <button class="btn blue" onclick="openClientWorkspace(${c.id})">Abrir painel</button>
        <button class="btn" onclick="openClientAccess(${c.id})">Gerenciar acesso</button>
        <button class="btn" onclick="openContentFor(${c.id})">Gerenciar conteúdo</button>
      </div></td>`;
    tb.appendChild(tr);
  });
}
function openNewClient(){
  ['nc-name','nc-email','nc-company','nc-segment'].forEach(id=>document.getElementById(id).value='');
  openModal('new-client-modal');
}
function createClient(){
  if(!isAdmin) return;
  const name=document.getElementById('nc-name').value.trim();
  const email=document.getElementById('nc-email').value.trim().toLowerCase();
  const company=document.getElementById('nc-company').value.trim();
  const segment=document.getElementById('nc-segment').value.trim()||'—';
  if(!name || !company){ toast('Informe o nome do responsável e a empresa.','error'); return; }
  if(!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)){ toast('Informe um e-mail de acesso válido.','error'); return; }
  if(staff.some(x=>x.email.toLowerCase()===email) || clients.some(x=>(x.email||'').toLowerCase()===email)){
    toast('Já existe um acesso com esse e-mail.','error'); return;
  }
  const id = Math.max(...clients.map(c=>c.id)) + 1;
  clients.push({
    id, role:'client', name, email, company, segment,
    version:0, planFile:'— nenhum plano —', updated:'—', status:'Pendente', suspended:false,
    lastLogin:'Nunca acessou', consultas:0, answered:0, asked:0,
    tabs:defaultTabs(), tabHits:{}, faq:[], scope:{}, conversations:[], activeConvId:null
  });
  closeModal('new-client-modal');
  renderAdminTable();
  persist();
  toast('Acesso criado para ' + name + '.','ok');
}

/* ============================================================
   12b. ADMIN — CONTEÚDO DOS PLANOS
   ============================================================ */
function openContentFor(id){ if(!isAdmin) return; activeClientId=id; activeTabId=null; updateShell(); go('conteudo'); }
function initConteudo(){
  const sel=document.getElementById('ct-client');
  sel.innerHTML=clients.map(c=>`<option value="${c.id}">${esc(c.company)} — ${esc(c.name)}</option>`).join('');
  sel.value=String(activeClientId);
  renderContentAdmin();
}
function selectContentClient(id){ activeClientId=Number(id); activeTabId=null; updateShell(); renderContentAdmin(); }
function renderContentAdmin(){
  const list=document.getElementById('ct-list');
  if(!list) return;
  const c=C(), all=c.tabs;
  const done=all.filter(t=>t.attachment.processed).length;
  document.getElementById('ct-summary').innerHTML =
    `<b>${esc(c.company)}</b> · ${all.length} seções · ${done} processadas · última atualização: ${esc(c.updated||'—')}`;
  if(!all.find(t=>t.id===activeTabId)) activeTabId = all[0] ? all[0].id : null;
  list.innerHTML = all.map(t=>{
    const st=stageStatus(t);
    return `<button type="button" class="ct-row${t.id===activeTabId?' active':''}" onclick="selectContentTab('${t.id}')"${t.id===activeTabId?' aria-current="true"':''}>
      <span class="nm">${esc(t.name)}</span><span class="pill ${st.cls}">${st.label}</span></button>`;
  }).join('');
  renderContentDetail();
}
function selectContentTab(id){ activeTabId=id; renderContentAdmin(); }
function renderContentDetail(){
  const box=document.getElementById('ct-detail');
  if(!box) return;
  const t=currentTab();
  if(!t){ box.innerHTML='<p class="empty-note">Este plano ainda não tem seções.</p>'; return; }
  const a=t.attachment, m=Object.assign(emptyMeta(), t.meta||{}), st=stageStatus(t);
  const hasSrc=!!(a.pdfName||a.slideLink);
  box.innerHTML=`
    <div class="ct-detail-head"><h2>${esc(t.name)}</h2><span class="pill ${st.cls}">${st.label}</span></div>
    <p class="ct-meta">Atualizado em ${esc(a.processedAt||m.updatedAt||'—')}</p>
    ${a.error?`<div class="form-error" role="alert">${esc(a.error)}</div>`:''}
    <div class="field"><label for="ct-title">Título exibido ao cliente</label><input id="ct-title" type="text" value="${esc(t.title||t.name)}"></div>
    <div class="field"><label for="ct-short">Descrição curta</label><input id="ct-short" type="text" value="${esc(m.shortDescription)}"></div>
    <div class="field"><label for="ct-what">O que é esta etapa?</label><textarea id="ct-what" rows="2">${esc(m.whatIsIt)}</textarea></div>
    <div class="field"><label for="ct-obj">Objetivo</label><textarea id="ct-obj" rows="2">${esc(m.objective)}</textarea></div>
    <div class="field"><label for="ct-points">Principais pontos <span class="hint">(um por linha)</span></label><textarea id="ct-points" rows="4">${esc(m.keyPoints.join('\n'))}</textarea></div>
    <div class="field"><label for="ct-questions">Perguntas sugeridas <span class="hint">(uma por linha)</span></label><textarea id="ct-questions" rows="3">${esc(m.suggestedQuestions.join('\n'))}</textarea></div>
    <div class="ct-actions"><button class="btn blue" onclick="saveContentMeta()">Salvar metadados</button></div>

    <h3 class="ct-sub">Fonte</h3>
    <div class="attach-file-row">
      <span class="fn ${a.pdfName?'':'empty'}">${a.pdfName?esc(a.pdfName):'Nenhum PDF anexado'}</span>
      <button class="btn" onclick="triggerPdfUpload()">${a.pdfName?'Substituir fonte':'Anexar PDF'}</button>
    </div>
    <input type="file" id="pdf-file-input" accept=".pdf" class="hidden" onchange="onPdfSelected(this)" aria-label="Arquivo PDF desta etapa">
    <div class="attach-link-row" style="margin-top:10px;">
      <input type="text" id="slide-link-input" placeholder="Link (slide, doc, planilha...)" aria-label="Link da fonte" value="${esc(a.slideLink)}">
      <button class="btn" onclick="saveSlideLink()">Salvar link</button>
    </div>
    <div class="ct-actions">
      <button class="btn yellow" onclick="processDoc()" ${hasSrc?'':'disabled'}>${a.processed?'Reprocessar com IA':'Processar com IA'}</button>
      <button class="btn" onclick="togglePublish()" ${a.processed?'':'disabled'}>${t.published===false?'Publicar para o cliente':'Despublicar'}</button>
      <button class="btn" onclick="openClientWorkspace(${C().id})">Ver como o cliente</button>
    </div>
    <div id="attach-status" class="ct-status" aria-live="polite"></div>

    <h3 class="ct-sub">Conteúdo atual</h3>
    <div class="body-text ct-preview">${t.html||emptyTabHtml()}</div>`;
}
function saveContentMeta(){
  if(!isAdmin) return;
  const t=currentTab(); if(!t) return;
  const v=id=>document.getElementById(id).value.trim();
  const lines=id=>v(id).split(/\n+/).map(x=>x.trim()).filter(Boolean);
  t.title=v('ct-title')||t.name;
  t.meta=Object.assign(emptyMeta(), t.meta||{}, {shortDescription:v('ct-short'), whatIsIt:v('ct-what'), objective:v('ct-obj'),
    keyPoints:lines('ct-points'), suggestedQuestions:lines('ct-questions')});
  persist(); renderContentAdmin();
  toast('Metadados salvos.','ok');
}
function togglePublish(){
  if(!isAdmin) return;
  const t=currentTab(); if(!t) return;
  t.published = (t.published===false);
  persist(); renderContentAdmin();
  toast(t.published ? 'Etapa publicada para o cliente.' : 'Etapa retirada da visão do cliente.','ok');
}

let accessTab='login', accessClient=null;
function openClientAccess(id){
  accessClient=clients.find(x=>x.id===id);
  document.getElementById('access-modal-title').textContent=accessClient.name;
  document.getElementById('access-modal-sub').textContent=accessClient.company+' · '+accessClient.email;
  accessTab='login';
  renderAccessModal();
  openModal('client-access-modal');
}
function setAccessTab(t){ accessTab=t; renderAccessModal(); }
function renderAccessModal(){
  const c=accessClient;
  document.getElementById('access-tab-login').classList.toggle('active', accessTab==='login');
  document.getElementById('access-tab-scope').classList.toggle('active', accessTab==='scope');
  const body=document.getElementById('access-modal-body');
  if(accessTab==='login'){
    body.innerHTML=`
      <div class="access-login-row"><span class="k">E-mail de acesso</span><span class="v">${esc(c.email)}</span></div>
      <div class="access-login-row"><span class="k">Empresa vinculada</span><span class="v">${esc(c.company)}</span></div>
      <div class="access-login-row"><span class="k">Último acesso</span><span class="v">${esc(c.lastLogin)}</span></div>
      <div class="access-login-row"><span class="k">Status do plano</span><span class="v">${esc(c.status)}</span></div>
      <div class="access-login-row"><span class="k">Status do acesso</span><span class="v">${c.suspended?'Suspenso':'Ativo'}</span></div>
      <div style="display:flex;flex-direction:column;gap:8px;margin-top:18px;">
        <button class="btn blue" onclick="closeModal('client-access-modal');openClientWorkspace(${c.id})">Abrir o painel deste cliente</button>
        <button class="btn" onclick="toast('O envio de e-mail depende do servidor (versão Java). Por enquanto, combine a nova senha direto com o cliente.','info')">Redefinir senha</button>
        <button class="btn danger" onclick="toggleSuspend(${c.id})">${c.suspended?'Reativar acesso':'Suspender acesso'}</button>
      </div>`;
  } else {
    body.innerHTML=`
      <p style="font-size:13px;color:var(--muted);margin:0 0 10px;">Marque as abas que este cliente pode ver e consultar no chat.</p>
      <div class="scope-scroll">
        <div class="scope-group-title">Abas do plano de ${esc(c.company)}</div>
        ${c.tabs.map(t=>`<label class="scope-item">
          <input type="checkbox" ${c.scope[t.id]!==false?'checked':''} onchange="toggleScope('${t.id}',this.checked)">
          <span>${esc(t.name)}</span></label>`).join('')}
      </div>`;
  }
}
function toggleScope(id,val){ accessClient.scope[id]=val; persist(); }
function toggleSuspend(id){
  if(!isAdmin) return;
  const c=clients.find(x=>x.id===id);
  if(!c) return;
  c.suspended=!c.suspended;
  toast(c.suspended ? 'Acesso de '+c.name+' suspenso.' : 'Acesso de '+c.name+' reativado.','ok');
  if(accessClient && accessClient.id===c.id) renderAccessModal();
  renderAdminTable(); persist();
}

/* ============================================================
   13. PERSISTÊNCIA NO NAVEGADOR
   O site não tem servidor: o plano, as abas e as conversas ficam
   guardados no localStorage deste navegador. Se ele não estiver
   disponível (janela privada, file:// em alguns navegadores), a
   aplicação segue funcionando só na memória da aba.
   ============================================================ */
const STORAGE_KEY = 'chatjr.v1';
let storageWorks = (function(){
  try{
    const probe='__chatjr_probe__';
    window.localStorage.setItem(probe,'1');
    window.localStorage.removeItem(probe);
    return true;
  }catch(e){ return false; }
})();

/**
 * Remove dados pesados da aba (base64 do PDF) antes de salvar no
 * localStorage. O texto extraído é limitado a 20 mil caracteres.
 */
function cleanTabForStorage(t){
  const a=t.attachment||{};
  return {
    id:t.id, name:t.name, title:t.title, html:t.html, published:t.published!==false, meta:t.meta||emptyMeta(),
    attachment:{
      pdfName:a.pdfName||'', pdfText:(a.pdfText||'').slice(0,20000), pdfData:null,
      slideLink:a.slideLink||'', processed:!!a.processed, extracted:a.extracted||0,
      sections:a.sections||[], knowledge:a.knowledge||'', processedAt:a.processedAt||'', error:''
    }
  };
}
function snapshot(){
  return {
    version:1,
    loginAt,
    theme:currentTheme,
    model:AI_MODEL,
    loggedIn: !document.getElementById('app').classList.contains('hidden'),
    sessionClientId, activeClientId,
    clients: clients.map(c=>({
      id:c.id, name:c.name, email:c.email, company:c.company, segment:c.segment,
      version:c.version, planFile:c.planFile, updated:c.updated, status:c.status,
      role:c.role||'client', suspended:!!c.suspended,
      lastLogin:c.lastLogin, consultas:c.consultas, asked:c.asked, answered:c.answered,
      tabs:(c.tabs||[]).map(cleanTabForStorage),
      conversations:c.conversations||[], activeConvId:c.activeConvId,
      scope:c.scope||{}, tabHits:c.tabHits||{}, faq:c.faq||[],
      queryTypes:c.queryTypes||{}, unanswered:c.unanswered||[]
    }))
  };
}
function persist(){
  if(!storageWorks) return;
  try{ window.localStorage.setItem(STORAGE_KEY, JSON.stringify(snapshot())); }
  catch(e){ storageWorks=false; }
}
function restore(){
  if(!storageWorks) return false;
  let raw;
  try{ raw=window.localStorage.getItem(STORAGE_KEY); }catch(e){ return false; }
  if(!raw) return false;
  let data;
  try{ data=JSON.parse(raw); }catch(e){ return false; }
  if(!data || !Array.isArray(data.clients) || !data.clients.length) return false;

  clients = data.clients.map(c=>{
    c.tabs = (c.tabs||[]).map(t=>{
      const tab = makeTab(t.name||'Aba', t.html||'');
      tab.id = t.id || tab.id;
      tab.title = t.title || tab.name;
      Object.assign(tab.attachment, t.attachment||{});
      tab.published = t.published!==false;
      tab.meta = t.meta ? Object.assign(emptyMeta(), t.meta) : (tab.attachment.processed ? deriveMeta(tab) : emptyMeta());
      return tab;
    });
    c.conversations = c.conversations||[];
    c.scope = c.scope||{}; c.tabHits = c.tabHits||{}; c.faq = c.faq||[]; c.queryTypes = c.queryTypes||{}; c.unanswered = c.unanswered||[];
    return c;
  });
  // o perfil sempre é derivado da conta, nunca de um valor salvo à parte
  const known = data.sessionClientId && (staff.some(x=>x.id===data.sessionClientId) || clients.some(x=>x.id===data.sessionClientId));
  sessionClientId = known ? data.sessionClientId : null;
  isAdmin = staff.some(x=>x.id===sessionClientId);
  adminEditing = isAdmin;
  activeClientId = clients.some(x=>x.id===data.activeClientId) ? data.activeClientId : (isAdmin ? clients[0].id : sessionClientId);
  if(data.model) AI_MODEL=data.model;
  applyTheme(data.theme==='dark' ? 'dark' : 'light');
  applyRole();
  if(sessionClientId) updateShell();
  loginAt = data.loginAt || 0;
  if(data.loggedIn && sessionClientId && (Date.now()-loginAt) > SESSION_MS){
    sessionClientId=null; isAdmin=false; adminEditing=false; applyRole();
    loginFail('Sua sessão expirou. Entre novamente para continuar.');
  } else if(data.loggedIn && sessionClientId && !isAdmin && sessionUser().suspended){
    sessionClientId=null; applyRole();
    loginFail('Seu acesso foi suspenso. Fale com a consultoria da Empresa JR.');
  }
  if(data.loggedIn && sessionClientId){
    document.getElementById('view-auth').classList.add('hidden');
    document.getElementById('app').classList.remove('hidden');
    go(isAdmin ? 'admin' : 'dashboard');
    probeAI();
  }
  return true;
}
function storageInfo(){
  if(!storageWorks) return 'Este navegador não permite guardar dados. As alterações valem só até fechar a aba.';
  let bytes=0;
  try{ bytes=(window.localStorage.getItem(STORAGE_KEY)||'').length; }catch(e){}
  const kb=(bytes/1024).toFixed(1).replace('.',',');
  return kb+' KB guardados neste navegador';
}
function exportData(){
  const blob=new Blob([JSON.stringify(snapshot(),null,2)],{type:'application/json'});
  const url=URL.createObjectURL(blob);
  const a=document.createElement('a');
  a.href=url;
  a.download='chat-jr-'+slugify(C().company)+'.json';
  document.body.appendChild(a); a.click(); a.remove();
  setTimeout(()=>URL.revokeObjectURL(url), 2000);
}
function importData(input){
  if(!(input.files&&input.files[0])) return;
  const file=input.files[0];
  const reader=new FileReader();
  reader.onload=()=>{
    let data;
    try{ data=JSON.parse(String(reader.result)); }
    catch(e){ toast('Esse arquivo não é uma cópia válida do Chat Jr.','error'); return; }
    if(!data || !Array.isArray(data.clients) || !data.clients.length){
      toast('Esse arquivo não tem nenhum plano dentro.','error'); return;
    }
    if(!confirm('Substituir os dados deste navegador pelo conteúdo de "'+file.name+'"?')) return;
    try{ window.localStorage.setItem(STORAGE_KEY, JSON.stringify(data)); }catch(e){}
    window.location.reload();
  };
  reader.onerror=()=>toast('Não foi possível ler o arquivo.','error');
  reader.readAsText(file);
  input.value='';
}
function resetData(){
  if(!confirm('Apagar os dados guardados neste navegador e voltar ao plano de demonstração?')) return;
  try{ window.localStorage.removeItem(STORAGE_KEY); }catch(e){}
  window.location.reload();
}

// liga a persistência ao ciclo de vida da aplicação
const baseGo = go;
go = function(view){ baseGo(view); persist(); };
const baseDoLogin = doLogin;
doLogin = function(){ baseDoLogin(); persist(); };
const baseDoLogout = doLogout;
doLogout = function(){ baseDoLogout(); persist(); };

window.addEventListener('beforeunload', persist);
setInterval(persist, 4000);

/* ============================================================
   14. INÍCIO
   ============================================================ */
setAuthTab('login');
updateShell();
restore();
