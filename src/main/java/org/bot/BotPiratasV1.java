package org.bot;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.security.auth.login.LoginException;
import java.awt.*;
import java.awt.Color;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.security.auth.login.LoginException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

public class BotPiratasV1 extends ListenerAdapter {
    private static final Map<String, Double> recursos = new HashMap<>();
    private static final Map<String, Map<String, Double>> usuariosRecursos = new HashMap<>();

    static {
        recursos.put("dinheiro_sujo", 0.0);
        recursos.put("cocaina_azul", 0.0);
        recursos.put("cocaina_branca", 0.0);

        // Inicializa usuariosRecursos para cada recurso em recursos
        for (String recurso : recursos.keySet()) {
            usuariosRecursos.put(recurso, new HashMap<>());
        }
    }

    public static void main(String[] args) throws LoginException {
        JDA jda = JDABuilder.createDefault("token_aqui")
                .addEventListeners(new BotPiratasV1())
                .build();
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        String[] messageContent = event.getMessage().getContentRaw().split(" ");
        String command = messageContent[0];

        switch (command) {
            case ".ajuda":
                event.getChannel().sendMessageEmbeds(criarEmbedAjuda(event.getAuthor())).queue();
                break;
            case ".farm":
                handleFarmCommand(event, messageContent);
                break;
            case ".estoque":
                event.getChannel().sendMessageEmbeds(criarEmbedPainelConsolidado(event.getAuthor())).queue();
                break;
            case ".registros":
                event.getChannel().sendMessageEmbeds(criarEmbedRegistros(event.getJDA())).queue();
                try {
                    gerarExcelRegistros(event.getJDA());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                break;
            case ".meta":
                if (messageContent.length == 2) {
                    String recurso = messageContent[1].toLowerCase();
                    if (recursos.containsKey(recurso)) {
                        event.getChannel().sendMessageEmbeds(criarEmbedMeta(event.getAuthor(), recurso)).queue();
                    } else {
                        event.getChannel().sendMessage("Recurso inválido. Os recursos disponíveis para meta são: " + String.join(", ", recursos.keySet())).queue();
                    }
                } else {
                    event.getChannel().sendMessage("Especifique um recurso válido. Os recursos disponíveis para meta são: " + String.join(", ", recursos.keySet())).queue();
                }
                break;
        }
    }

    private MessageEmbed criarEmbedRegistros(JDA jda) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Registros de Recursos por Usuário");
        embed.setColor(Color.YELLOW);

        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));

        for (String recurso : recursos.keySet()) {
            Map<String, Double> usuarios = usuariosRecursos.get(recurso);
            if (usuarios != null && !usuarios.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("**").append(recurso.replace("_", " ").toUpperCase()).append("**\n");

                for (Map.Entry<String, Double> entry : usuarios.entrySet()) {
                    String userId = entry.getKey();
                    double quantidade = entry.getValue();

                    User user = jda.getUserById(userId);
                    String userName = (user != null) ? user.getAsMention() : userId;
                    sb.append(userName).append(": ").append(format.format(quantidade)).append("\n");
                }

                embed.addField(recurso.replace("_", " ").toUpperCase(), sb.toString(), false);
            } else {
                embed.addField(recurso.replace("_", " ").toUpperCase(), "Nenhum registro encontrado.", false);
            }
        }

        return embed.build();
    }

    private void handleFarmCommand(MessageReceivedEvent event, String[] messageContent) {
        if (messageContent.length == 2) {
            String recurso = messageContent[1].toLowerCase();
            if (recursos.containsKey(recurso)) {
                NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
                String totalFormatado = format.format(recursos.get(recurso));
                event.getChannel().sendMessage("Total de " + recurso.replace("_", " ") + ": " + totalFormatado).queue();
            } else {
                event.getChannel().sendMessage("Recurso inválido. Os recursos disponíveis são: " + String.join(", ", recursos.keySet())).queue();
            }
        } else if (messageContent.length == 4) {
            String recurso = messageContent[1].toLowerCase();
            String action = messageContent[2].toLowerCase();
            int amount;
            try {
                amount = Integer.parseInt(messageContent[3]);
            } catch (NumberFormatException e) {
                event.getChannel().sendMessage("A quantidade precisa ser um número inteiro positivo.").queue();
                return;
            }

            if (recursos.containsKey(recurso)) {
                if (action.equals("add")) {
                    adicionarRecurso(event, recurso, amount);
                } else if (action.equals("remove")) {
                    removerRecurso(event, recurso, amount);
                } else {
                    event.getChannel().sendMessage("Ação inválida. Use 'add' ou 'remove'.").queue();
                }
            } else {
                event.getChannel().sendMessage("Recurso inválido. Os recursos disponíveis são: " + String.join(", ", recursos.keySet())).queue();
            }
        } else {
            event.getChannel().sendMessage("Uso inválido do comando. Tente `.farm <recurso>`, `.farm <recurso> add <quantidade>` ou `.farm <recurso> remove <quantidade>`.").queue();
        }
    }

    private void adicionarRecurso(MessageReceivedEvent event, String recurso, int amount) {
        User user = event.getAuthor();
        String userId = user.getId();

        // Verifica se usuariosRecursos contém a chave para o recurso
        if (!usuariosRecursos.containsKey(recurso)) {
            usuariosRecursos.put(recurso, new HashMap<>());
        }

        double quantidadeAdicionada = usuariosRecursos.get(recurso).getOrDefault(userId, 0.0);

        recursos.put(recurso, recursos.get(recurso) + amount);
        usuariosRecursos.get(recurso).put(userId, quantidadeAdicionada + amount);
        event.getChannel().sendMessage(amount + " adicionado a " + recurso.replace("_", " ") + ".").queue();
    }

    private void removerRecurso(MessageReceivedEvent event, String recurso, int amount) {
        if (recursos.get(recurso) >= amount) {
            recursos.put(recurso, recursos.get(recurso) - amount);
            event.getChannel().sendMessage(amount + " removido de " + recurso.replace("_", " ") + ".").queue();
        } else {
            event.getChannel().sendMessage("Você não possui quantidade suficiente de " + recurso.replace("_", " ") + " para remover.").queue();
        }
    }

    private MessageEmbed criarEmbedAjuda(User user) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Comandos Disponíveis");
        embed.setDescription("Aqui está uma lista de todos os comandos disponíveis:");
        embed.setColor(Color.BLUE);

        embed.addField(".farm <recurso>", "Mostra o total do recurso especificado.", false);
        embed.addField(".farm <recurso> add <quantidade>", "Adiciona a quantidade especificada ao recurso.", false);
        embed.addField(".farm <recurso> remove <quantidade>", "Remove a quantidade especificada do recurso.", false);
        embed.addField(".farm cocaina", "Mostra o painel consolidado dos recursos de cocaína (cocaina_azul e cocaina_branca).", false);
        embed.addField(".estoque", "Mostra o painel consolidado de todos os recursos.", false);
        embed.addField(".meta <recurso>", "Mostra a quantidade total adicionada ao recurso especificado.", false);

        embed.setFooter("Usuário: " + user.getName(), user.getAvatarUrl());
        return embed.build();
    }

    private MessageEmbed criarEmbedPainelConsolidado(User user) {
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Painel Consolidado de Recursos");
        embed.setColor(Color.BLUE);

        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("pt", "BR"));
        for (Map.Entry<String, Double> entry : recursos.entrySet()) {
            String recurso = entry.getKey();
            double total = entry.getValue();
            String totalFormatado = format.format(total);
            embed.addField(recurso.replace("_", " ").toUpperCase(), "Total: " + totalFormatado, false);
        }

        embed.setFooter("Usuário: " + user.getName(), user.getAvatarUrl());
        return embed.build();
    }

    private MessageEmbed criarEmbedMeta(User user, String recurso) {
        EmbedBuilder embed = new EmbedBuilder();
        String userId = user.getId();
        double quantidadeAdicionada = usuariosRecursos.get(recurso).getOrDefault(userId, 0.0);

        embed.setTitle("Meta de " + recurso.replace("_", " ").toUpperCase());
        embed.setDescription("Você adicionou " + quantidadeAdicionada + " ao recurso " + recurso.replace("_", " ") + ".");
        embed.setColor(Color.GREEN);
        embed.setFooter("Usuário: " + user.getName(), user.getAvatarUrl());
        return embed.build();
    }

    private void gerarExcelRegistros(JDA jda) throws IOException {
        Workbook workbook = new XSSFWorkbook();
        CreationHelper createHelper = workbook.getCreationHelper();
        Sheet sheet = workbook.createSheet("Registros de Recursos");

        // Estilo para os cabeçalhos das colunas
        CellStyle headerCellStyle = workbook.createCellStyle();
        headerCellStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
        headerCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font headerFont = workbook.createFont();
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerCellStyle.setFont(headerFont);

        // Cabeçalhos das colunas
        Row headerRow = sheet.createRow(0);
        headerRow.createCell(0).setCellValue("Recurso");
        headerRow.createCell(1).setCellValue("Usuário");
        headerRow.createCell(2).setCellValue("Quantidade");

        // Estilo para as células de dados
        CellStyle dataCellStyle = workbook.createCellStyle();
        dataCellStyle.setDataFormat(createHelper.createDataFormat().getFormat("#,##0.00"));

        int rowNum = 1;
        for (String recurso : recursos.keySet()) {
            Map<String, Double> usuarios = usuariosRecursos.get(recurso);
            if (usuarios != null) {
                for (Map.Entry<String, Double> entry : usuarios.entrySet()) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(recurso.replace("_", " ").toUpperCase());

                    String userId = entry.getKey();
                    double quantidade = entry.getValue();

                    User discordUser = jda.getUserById(userId);
                    String userName = (discordUser != null) ? discordUser.getName() : userId;
                    row.createCell(1).setCellValue(userName);
                    row.createCell(2).setCellValue(quantidade);
                }
            }
        }

        // Ajusta a largura das colunas
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
        sheet.autoSizeColumn(2);

        // Salva o arquivo Excel
        FileOutputStream fileOut = new FileOutputStream("RegistrosDeRecursos.xlsx");
        workbook.write(fileOut);
        fileOut.close();

        // Fecha o workbook para liberar recursos
        workbook.close();
    }
}
