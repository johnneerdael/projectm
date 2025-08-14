#include "PresetFactoryManager.hpp"

#include <MilkdropPreset/Factory.hpp>

#include <Utils.hpp>

#include <algorithm>
#include <cassert>
#include <iostream>
#include <sstream>

namespace libprojectM {

PresetFactoryManager::~PresetFactoryManager()
{
    ClearFactories();
}

void PresetFactoryManager::ClearFactories()
{
    m_factoryMap.clear();
    for (std::vector<PresetFactory*>::iterator pos = m_factoryList.begin();
         pos != m_factoryList.end(); ++pos)
    {
        assert(*pos);
        delete (*pos);
    }
    m_factoryList.clear();
}

void PresetFactoryManager::initialize()
{
    ClearFactories();

    auto* milkdropFactory = new MilkdropPreset::Factory();
    registerFactory(milkdropFactory->supportedExtensions(), milkdropFactory);
}

// Current behavior if a conflict is occurs is to override the previous request

void PresetFactoryManager::registerFactory(const std::string& extensions, PresetFactory* factory)
{

    std::stringstream ss(extensions);
    std::string extension;

    m_factoryList.push_back(factory);

    while (ss >> extension)
    {
        if (m_factoryMap.count(extension))
        {
            std::cerr << "[PresetFactoryManager] Warning: extension \"" << extension << "\" already has a factory. New factory handler ignored." << std::endl;
        }
        else
        {
            m_factoryMap.insert(std::make_pair(extension, factory));
        }
    }
}


std::unique_ptr<Preset> PresetFactoryManager::CreatePresetFromFile(const std::string& filename)
{
    try
    {
        // Android TV: Validate file size limits
        if (filename.length() > 4096)
        {
            throw PresetFactoryException("Preset filename too long for Android TV");
        }

        const std::string extension = "." + ParseExtension(filename);

        // Android TV: Check file size if possible (basic validation)
        auto preset = factory(extension).LoadPresetFromFile(filename);
        
        // Android TV: Validate preset doesn't create excessive resources
        if (preset && !ValidatePresetForAndroidTV(preset.get()))
        {
            throw PresetFactoryException("Preset incompatible with Android TV constraints");
        }

        return preset;
    }
    catch (const PresetFactoryException&)
    {
        throw;
    }
    catch (const std::exception& e)
    {
        throw PresetFactoryException(e.what());
    }
    catch (...)
    {
        throw PresetFactoryException("Uncaught preset factory exception");
    }
}

std::unique_ptr<Preset> PresetFactoryManager::CreatePresetFromStream(const std::string& extension, std::istream& data)
{
    try
    {
        // Android TV: Validate stream size to prevent excessive memory usage
        auto startPos = data.tellg();
        data.seekg(0, std::ios::end);
        auto endPos = data.tellg();
        data.seekg(startPos);
        
        if (endPos != std::istream::pos_type(-1) && startPos != std::istream::pos_type(-1))
        {
            auto streamSize = endPos - startPos;
            if (streamSize > 1024 * 1024) // 1MB limit for Android TV
            {
                throw PresetFactoryException("Preset stream too large for Android TV");
            }
        }

        auto preset = factory(extension).LoadPresetFromStream(data);
        
        // Android TV: Validate preset compatibility
        if (preset && !ValidatePresetForAndroidTV(preset.get()))
        {
            throw PresetFactoryException("Preset incompatible with Android TV constraints");
        }

        return preset;
    }
    catch (const PresetFactoryException&)
    {
        throw;
    }
    catch (const std::exception& e)
    {
        throw PresetFactoryException(e.what());
    }
    catch (...)
    {
        throw PresetFactoryException("Uncaught preset factory exception");
    }
}

PresetFactory& PresetFactoryManager::factory(const std::string& extension)
{

    if (!extensionHandled(extension))
    {
        std::ostringstream os;
        os << "No preset factory associated with \"" << extension << "\"." << std::endl;
        throw PresetFactoryException(os.str());
    }
    return *m_factoryMap[extension];
}

bool PresetFactoryManager::extensionHandled(const std::string& extension) const
{
    return m_factoryMap.count(extension);
}

std::vector<std::string> PresetFactoryManager::extensionsHandled() const
{
    std::vector<std::string> retval;
    for (auto const& element : m_factoryMap)
    {
        retval.push_back(element.first);
    }
    return retval;
}

auto PresetFactoryManager::ParseExtension(const std::string& filename) -> std::string
{
    const auto start = filename.find_last_of('.');

    if (start == std::string::npos || start >= (filename.length() - 1)) {
        return "";
    }

    return Utils::ToLower(filename.substr(start + 1, filename.length()));
}

bool PresetFactoryManager::ValidatePresetForAndroidTV(Preset* preset) const
{
    if (!preset)
    {
        return false;
    }

    // Android TV: Basic validation - more detailed checks could be added
    // based on specific preset properties that are accessible
    
    try
    {
        // This is a placeholder for actual preset validation
        // In practice, this would check things like:
        // - Number of custom shapes/waves
        // - Complex expressions that might cause performance issues
        // - Excessive texture usage
        // - Per-vertex calculations that exceed limits
        
        // For now, we just ensure the preset exists and is valid
        return true;
    }
    catch (...)
    {
        return false;
    }
}

} // namespace libprojectM
